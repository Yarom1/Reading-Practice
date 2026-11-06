package com.korimbekef.reading.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.korimbekef.reading.core.DualText
import com.korimbekef.reading.core.HebrewText
import com.korimbekef.reading.core.LineTokens
import com.korimbekef.reading.core.SpokenText
import com.korimbekef.reading.data.prefs.AppSettings
import com.korimbekef.reading.data.prefs.SettingsRepository
import com.korimbekef.reading.data.prefs.SyncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Android TextToSpeech with word-synchronised highlighting.
 *
 * Two strategies, chosen automatically:
 *  1. ENGINE_RANGES — the whole line is one utterance and `onRangeStart` tells us which characters
 *     are being spoken right now. Used only after a short self-check proved that the current voice
 *     really reports word positions (and whether they refer to the text with or without nikud).
 *  2. WORD_BY_WORD — every word is its own utterance, queued back to back; a word is highlighted
 *     when the engine reports that its audio started. Always in sync, slightly less fluent.
 * There is no timer anywhere: the highlight only moves on events coming from the engine.
 *
 * All state is touched on the main thread; engine callbacks are posted to it. Every request gets a
 * new "generation", so late callbacks of an interrupted request are ignored and two voices never
 * overlap (tapping another line flushes the queue first).
 */
class TtsSpeechController(
    context: Context,
    private val settingsRepo: SettingsRepository,
    private val scope: CoroutineScope,
) : SpeechController {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(SpeechState())
    private val _playback = MutableStateFlow(Playback())
    override val state: StateFlow<SpeechState> = _state.asStateFlow()
    override val playback: StateFlow<Playback> = _playback.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready = false
    private var engineInitialized = false
    private var settings = AppSettings()
    private var voicesByName: Map<String, Voice> = emptyMap()

    private var generation = 0
    private var requestCounter = 0L
    private var active: Active? = null
    private var paused: Pair<Active, Pair<Int, Int>>? = null
    private var pending: Pair<Long, SpeakRequest>? = null

    /** A message asked for while the engine was still starting; it is said as soon as the voice is ready. */
    private var pendingMessage: Pair<String, Float>? = null
    private var lastMessage: Message? = null

    /** Lines as sent to the engine (see [SpokenText]), by their text with nikud. */
    private val spokenCache = HashMap<String, LineTokens>()

    private var probe: Probe? = null
    private var probeCounter = 0
    private var probeAttempts = 0

    /** Engines already tried in automatic mode (e.g. a default engine without Hebrew). */
    private val triedEngines = mutableSetOf<String>()

    private class Active(
        val id: Long,
        val gen: Int,
        val request: SpeakRequest,
        val usePlain: Boolean,
        val lastLine: Int,
        val lastWord: Int,
    ) {
        var rangeEvents = 0
    }

    private class Probe(val key: String, val utteranceId: String) {
        val starts = ArrayList<Int>()
    }

    private data class Uid(val gen: Int, val type: Char, val line: Int, val word: Int)

    private class Message(val gen: Int, val text: String, val rate: Float)

    init {
        scope.launch { settingsRepo.settings.collect { onSettings(it) } }
    }

    // ------------------------------------------------------------------ settings & init

    private fun onSettings(s: AppSettings) {
        val old = settings
        settings = s
        if (!engineInitialized || s.enginePackage != old.enginePackage) {
            triedEngines.clear()
            initEngine(s.enginePackage)
            return
        }
        if (!ready) return
        if (s.voiceGenders != old.voiceGenders) loadVoices()
        if (s.voiceName != old.voiceName) applyVoice()
        if (s.syncMode != old.syncMode) publishSync()
    }

    private fun initEngine(pkg: String?) {
        engineInitialized = true
        ready = false
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        _state.value = _state.value.copy(status = EngineStatus.INITIALIZING, voices = emptyList(), currentVoice = null)
        lateinit var instance: TextToSpeech
        val listener = TextToSpeech.OnInitListener { status ->
            main.post { if (tts === instance) onInit(status, pkg) }
        }
        val created = runCatching {
            if (pkg != null) TextToSpeech(appContext, listener, pkg) else TextToSpeech(appContext, listener)
        }.getOrNull()
        if (created == null) {
            _state.value = SpeechState(status = EngineStatus.NO_ENGINE)
            return
        }
        instance = created
        tts = created
    }

    private fun onInit(status: Int, pkg: String?) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            if (pkg != null) {
                triedEngines += pkg
                initEngine(null)
            } else {
                _state.value = SpeechState(status = EngineStatus.NO_ENGINE)
                failPending("no-engine")
                pendingMessage = null
            }
            return
        }
        t.setOnUtteranceProgressListener(listener)
        runCatching {
            t.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
        }
        val engines = runCatching { t.engines.map { EngineInfo(it.name, it.label) } }.getOrDefault(emptyList())
        val enginePkg = pkg ?: runCatching { t.defaultEngine }.getOrNull()
        ready = true
        _state.value = _state.value.copy(engines = engines, enginePackage = enginePkg)
        loadVoices()
        applyVoice()
        pending?.let { (id, req) ->
            pending = null
            speakInternal(req, id)
        }
        pendingMessage?.let { (text, rate) ->
            pendingMessage = null
            speakMessage(text, rate)
        }
    }

    private fun loadVoices() {
        val t = tts ?: return
        val all = runCatching { t.voices?.toList() }.getOrNull().orEmpty()
        val hebrew = all
            .filter { isHebrew(it.locale) && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy<Voice>({ it.isNetworkConnectionRequired }, { -it.quality }, { it.name }))
        voicesByName = LinkedHashMap<String, Voice>().apply { hebrew.forEach { put(it.name, it) } }
        _state.value = _state.value.copy(
            voices = hebrew.mapIndexed { i, v ->
                VoiceInfo(
                    name = v.name,
                    label = "קול ${i + 1}",
                    isNetwork = v.isNetworkConnectionRequired,
                    quality = v.quality,
                    gender = settings.voiceGenders[v.name] ?: detectGender(v),
                )
            },
        )
    }

    private fun applyVoice() {
        val t = tts ?: return
        val chosen = settings.voiceName?.let { voicesByName[it] }
            ?: voicesByName.values.firstOrNull { !it.isNetworkConnectionRequired }
            ?: voicesByName.values.firstOrNull()
        var ok = false
        if (chosen != null) {
            ok = runCatching { t.setVoice(chosen) == TextToSpeech.SUCCESS }.getOrDefault(false)
        }
        if (!ok) {
            // Some engines do not list voices but still speak Hebrew through the language API.
            ok = listOf("he-IL", "iw-IL").any { tag ->
                val r = runCatching { t.setLanguage(Locale.forLanguageTag(tag)) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                r >= TextToSpeech.LANG_AVAILABLE
            }
        }
        if (!ok && settings.enginePackage == null) {
            // Automatic mode: the default engine has no Hebrew (common on Samsung phones),
            // so try the other installed engines, Google's first.
            _state.value.enginePackage?.let { triedEngines += it }
            val next = _state.value.engines.map { it.packageName }
                .filter { it !in triedEngines }
                .sortedByDescending { it == GOOGLE_TTS }
                .firstOrNull()
            if (next != null) {
                initEngine(next)
                return
            }
        }
        _state.value = _state.value.copy(
            status = if (ok) EngineStatus.READY else EngineStatus.NO_HEBREW,
            currentVoice = if (ok) chosen?.name else null,
        )
        if (ok) checkSyncSupport(force = false) else publishSync()
    }

    // ------------------------------------------------------------------ word-sync self check

    private fun syncKey(): String =
        "${_state.value.enginePackage ?: "default"}|${_state.value.currentVoice ?: "language"}"

    private fun checkSyncSupport(force: Boolean) {
        val cached = settings.rangeSupport[syncKey()]
        if (cached != null && !force) {
            setRange(runCatching { RangeSupport.valueOf(cached) }.getOrDefault(RangeSupport.UNKNOWN))
            return
        }
        startProbe()
    }

    private fun startProbe() {
        val t = tts ?: return
        if (!ready || _state.value.status != EngineStatus.READY) return
        val p = Probe(syncKey(), "P|${++probeCounter}")
        probe = p
        setRange(RangeSupport.UNKNOWN)
        val file = File(appContext.cacheDir, "tts_sync_check.wav")
        val result = runCatching { t.synthesizeToFile(PROBE_TEXT, Bundle(), file, p.utteranceId) }
            .getOrDefault(TextToSpeech.ERROR)
        if (result != TextToSpeech.SUCCESS) {
            finishProbe(p, completed = false)
            return
        }
        main.postDelayed({ if (probe === p) finishProbe(p, completed = false) }, 9000)
    }

    private fun finishProbe(p: Probe, completed: Boolean) {
        if (probe !== p) return
        probe = null
        val starts = p.starts.distinct()
        val withNikud = LineTokens.of(PROBE_TEXT)
        val withoutNikud = LineTokens.of(HebrewText.stripNikud(PROBE_TEXT))
        val result = when {
            starts.size >= 2 && starts.count { withNikud.isWordStart(it) } >= starts.size * 0.8 -> RangeSupport.SUPPORTED
            starts.size >= 2 && starts.count { withoutNikud.isWordStart(it) } >= starts.size * 0.8 -> RangeSupport.SUPPORTED_PLAIN
            completed -> RangeSupport.UNSUPPORTED
            else -> RangeSupport.UNKNOWN
        }
        setRange(result)
        if (result != RangeSupport.UNKNOWN) {
            scope.launch { settingsRepo.setRangeSupport(p.key, result.name) }
        }
    }

    private fun setRange(r: RangeSupport) {
        _state.value = _state.value.copy(rangeSupport = r)
        publishSync()
    }

    private fun publishSync() {
        _state.value = _state.value.copy(effectiveSync = effectiveSync(false))
    }

    private fun effectiveSync(forceWordByWord: Boolean): EffectiveSync {
        if (forceWordByWord) return EffectiveSync.WORD_BY_WORD
        return when (settings.syncMode) {
            SyncMode.WORD_BY_WORD -> EffectiveSync.WORD_BY_WORD
            SyncMode.ENGINE -> EffectiveSync.ENGINE_RANGES
            SyncMode.AUTO -> when (_state.value.rangeSupport) {
                RangeSupport.SUPPORTED, RangeSupport.SUPPORTED_PLAIN -> EffectiveSync.ENGINE_RANGES
                else -> EffectiveSync.WORD_BY_WORD
            }
        }
    }

    private fun usePlain(sync: EffectiveSync): Boolean =
        !settings.speakNikud || (sync == EffectiveSync.ENGINE_RANGES && _state.value.rangeSupport == RangeSupport.SUPPORTED_PLAIN)

    // ------------------------------------------------------------------ public API

    override fun speak(request: SpeakRequest): Long {
        val id = ++requestCounter
        paused = null
        speakInternal(request, id)
        return id
    }

    private fun speakInternal(request: SpeakRequest, id: Long) {
        val t = tts
        if (t == null || !ready) {
            if (_state.value.status == EngineStatus.INITIALIZING) {
                pending = id to request
                pendingMessage = null
                _playback.value = Playback(id, request.tag, PlayStatus.SPEAKING, request.startLine, -1)
            } else {
                _playback.value = Playback(id, request.tag, PlayStatus.ERROR, request.startLine, -1, "no-engine")
            }
            return
        }
        if (_state.value.status != EngineStatus.READY || request.lines.isEmpty()) {
            _playback.value = Playback(id, request.tag, PlayStatus.ERROR, request.startLine, -1, "no-hebrew")
            return
        }
        generation++
        val gen = generation
        val sync = effectiveSync(request.forceWordByWord || request.singleWord)
        val plain = usePlain(sync)
        val startLine = request.startLine.coerceIn(0, request.lines.lastIndex)
        val endLine = request.endLine.coerceIn(startLine, request.lines.lastIndex)
        runCatching {
            t.setSpeechRate(request.rate.coerceIn(0.3f, 2f))
            t.setPitch(settings.pitch)
        }
        var flush = true
        fun queueMode(): Int = if (flush) {
            flush = false
            TextToSpeech.QUEUE_FLUSH
        } else {
            TextToSpeech.QUEUE_ADD
        }

        var ok = true
        var lastLine = startLine
        var lastWord = 0
        if (request.singleWord) {
            val tokens = spoken(request.lines[startLine], plain)
            if (tokens.wordCount > 0) {
                val w = request.startWord.coerceIn(0, tokens.wordCount - 1)
                ok = say(t, tokens.speechWord(w), queueMode(), uid(gen, 'W', startLine, w))
                lastWord = w
            }
        } else {
            for (li in startLine..endLine) {
                val tokens = spoken(request.lines[li], plain)
                if (tokens.wordCount == 0) continue
                val first = if (li == startLine) request.startWord.coerceIn(0, tokens.wordCount - 1) else 0
                if (sync == EffectiveSync.ENGINE_RANGES) {
                    val from = tokens.words[first].start
                    ok = ok && say(t, tokens.text.substring(from), queueMode(), uid(gen, 'L', li, first))
                } else {
                    for (wi in first until tokens.wordCount) {
                        ok = ok && say(t, tokens.speechWord(wi), queueMode(), uid(gen, 'W', li, wi))
                    }
                }
                lastLine = li
                lastWord = tokens.wordCount - 1
                if (li < endLine) {
                    runCatching { t.playSilentUtterance(request.pauseBetweenLinesMs, TextToSpeech.QUEUE_ADD, uid(gen, 'S', li, 0)) }
                }
            }
        }
        active = Active(id, gen, request.copy(startLine = startLine, endLine = endLine), plain, lastLine, lastWord)
        _playback.value = if (ok) {
            Playback(id, request.tag, PlayStatus.SPEAKING, startLine, -1)
        } else {
            active = null
            Playback(id, request.tag, PlayStatus.ERROR, startLine, -1, "speak-failed")
        }
    }

    override fun pause() {
        val a = active ?: return
        val pb = _playback.value
        if (pb.status != PlayStatus.SPEAKING || pb.requestId != a.id) return
        val line = if (pb.line >= 0) pb.line else a.request.startLine
        val word = when {
            pb.word >= 0 -> pb.word
            line == a.request.startLine -> a.request.startWord
            else -> 0
        }
        generation++
        runCatching { tts?.stop() }
        active = null
        paused = a to (line to word)
        _playback.value = pb.copy(status = PlayStatus.PAUSED, line = line, word = word)
    }

    override fun resume() {
        val (a, pos) = paused ?: return
        paused = null
        val req = if (a.request.singleWord) a.request else a.request.copy(startLine = pos.first, startWord = pos.second)
        speakInternal(req, a.id)
    }

    override fun stop() {
        generation++
        active = null
        paused = null
        pending = null
        pendingMessage = null
        runCatching { tts?.stop() }
        val pb = _playback.value
        if (pb.status == PlayStatus.SPEAKING || pb.status == PlayStatus.PAUSED) {
            _playback.value = pb.copy(status = PlayStatus.IDLE, word = -1)
        }
    }

    override fun speakMessage(text: String, rate: Float) {
        val t = tts
        if (t == null || !ready) {
            // The voice is still starting (e.g. right after the app opened): say it when it is ready.
            if (_state.value.status == EngineStatus.INITIALIZING) {
                if (pending != null) stop()
                pendingMessage = text to rate
            }
            return
        }
        if (_state.value.status != EngineStatus.READY) return
        val pb = _playback.value
        if (pb.status == PlayStatus.SPEAKING || pb.status == PlayStatus.PAUSED) stop()
        pendingMessage = null
        generation++
        paused = null
        runCatching {
            t.setSpeechRate(rate.coerceIn(0.3f, 2f))
            t.setPitch(settings.pitch)
        }
        lastMessage = Message(generation, text, rate)
        say(t, spokenText(text), TextToSpeech.QUEUE_FLUSH, uid(generation, 'M', 0, 0))
    }

    override fun preview(voiceName: String?, text: String, rate: Float) {
        val t = tts ?: return
        if (!ready) return
        stop()
        generation++
        val saved = _state.value.currentVoice?.let { voicesByName[it] }
        val sample = voiceName?.let { voicesByName[it] }
        if (sample != null) runCatching { t.setVoice(sample) }
        runCatching {
            t.setSpeechRate(rate.coerceIn(0.3f, 2f))
            t.setPitch(settings.pitch)
        }
        say(t, spokenText(text), TextToSpeech.QUEUE_FLUSH, uid(generation, 'V', 0, 0))
        // The voice is captured when speak() is called, so the saved voice can be restored now.
        if (sample != null && saved != null && saved.name != sample.name) runCatching { t.setVoice(saved) }
    }

    override fun recheckSync() {
        if (!ready) return
        val key = syncKey()
        scope.launch { settingsRepo.setRangeSupport(key, null) }
        probeAttempts = 0
        startProbe()
    }

    // ------------------------------------------------------------------ engine callbacks

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val id = utteranceId ?: return
            main.post { handleStart(id) }
        }

        override fun onDone(utteranceId: String?) {
            val id = utteranceId ?: return
            main.post { handleDone(id) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            val id = utteranceId ?: return
            main.post { handleError(id, -1) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            val id = utteranceId ?: return
            main.post { handleError(id, errorCode) }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            val id = utteranceId ?: return
            main.post { handleStop(id) }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val id = utteranceId ?: return
            main.post { handleRange(id, start) }
        }
    }

    private fun handleStart(id: String) {
        val u = parse(id) ?: return
        val a = active ?: return
        if (u.gen != a.gen || u.gen != generation) return
        when (u.type) {
            'L' -> {
                a.rangeEvents = 0
                emit(a, u.line, u.word)
            }
            'W' -> emit(a, u.line, u.word)
        }
    }

    private fun handleRange(id: String, start: Int) {
        if (id.startsWith("P|")) {
            probe?.let { if (it.utteranceId == id) it.starts += start }
            return
        }
        val u = parse(id) ?: return
        val a = active ?: return
        if (u.type != 'L' || u.gen != a.gen || u.gen != generation) return
        val tokens = spoken(a.request.lines[u.line], a.usePlain)
        if (tokens.wordCount == 0) return
        val base = tokens.words[u.word.coerceIn(0, tokens.wordCount - 1)].start
        val index = tokens.wordIndexAt(base + start)
        a.rangeEvents++
        if (index >= u.word) emit(a, u.line, index)
    }

    private fun handleDone(id: String) {
        if (id.startsWith("P|")) {
            probe?.let { if (it.utteranceId == id) finishProbe(it, completed = true) }
            return
        }
        val u = parse(id) ?: return
        val a = active ?: return
        if (u.gen != a.gen || u.gen != generation) return
        if (u.type == 'L' && a.rangeEvents == 0 && settings.syncMode == SyncMode.AUTO) {
            val tokens = spoken(a.request.lines[u.line], a.usePlain)
            if (tokens.wordCount - u.word >= 3) {
                // The voice did not report word positions after all: use word-by-word from now on.
                setRange(RangeSupport.UNSUPPORTED)
                val key = syncKey()
                scope.launch { settingsRepo.setRangeSupport(key, RangeSupport.UNSUPPORTED.name) }
            }
        }
        val isLast = when (u.type) {
            'L' -> u.line == a.lastLine
            'W' -> u.line == a.lastLine && u.word == a.lastWord
            else -> false
        }
        if (isLast) {
            active = null
            _playback.value = Playback(a.id, a.request.tag, PlayStatus.DONE, u.line, -1)
            if (_state.value.rangeSupport == RangeSupport.UNKNOWN && probe == null && probeAttempts < 3) {
                main.postDelayed({
                    if (active == null && probe == null) {
                        probeAttempts++
                        checkSyncSupport(force = false)
                    }
                }, 1500)
            }
        }
    }

    private fun handleError(id: String, code: Int) {
        if (id.startsWith("P|")) {
            probe?.let { if (it.utteranceId == id) finishProbe(it, completed = false) }
            return
        }
        val u = parse(id) ?: return
        if (u.type == 'V') return
        if (u.type == 'M') {
            // A voice that needs the internet failed: say the message again with an offline voice.
            val m = lastMessage
            if (m != null && m.gen == u.gen && u.gen == generation && switchToOfflineVoice()) speakMessage(m.text, m.rate)
            return
        }
        val a = active ?: return
        if (u.gen != a.gen || u.gen != generation) return
        active = null
        // Any failure of a voice that needs the internet (no connection, timeout, server error):
        // continue from the same word with a voice that works without it.
        if (switchToOfflineVoice()) {
            speakInternal(a.request.copy(startLine = u.line, startWord = u.word), a.id)
            return
        }
        _playback.value = Playback(a.id, a.request.tag, PlayStatus.ERROR, u.line, -1, "tts-error-$code")
    }

    private fun handleStop(id: String) {
        if (id.startsWith("P|")) {
            probe?.let { if (it.utteranceId == id) finishProbe(it, completed = false) }
        }
    }

    private fun switchToOfflineVoice(): Boolean {
        val t = tts ?: return false
        val current = _state.value.currentVoice?.let { voicesByName[it] } ?: return false
        if (!current.isNetworkConnectionRequired) return false
        val local = voicesByName.values.firstOrNull { !it.isNetworkConnectionRequired } ?: return false
        if (runCatching { t.setVoice(local) }.getOrDefault(TextToSpeech.ERROR) != TextToSpeech.SUCCESS) return false
        _state.value = _state.value.copy(
            currentVoice = local.name,
            notice = "הקול שנבחר דורש אינטרנט, ולכן עברנו זמנית לקול שעובד בלי אינטרנט.",
        )
        return true
    }

    // ------------------------------------------------------------------ helpers

    private fun emit(a: Active, line: Int, word: Int) {
        _playback.value = Playback(a.id, a.request.tag, PlayStatus.SPEAKING, line, word)
    }

    /** The words as sent to the engine: same number and order as on screen (see [SpokenText]). */
    private fun spoken(line: DualText, plain: Boolean): LineTokens {
        if (plain) return line.plainTokens
        if (spokenCache.size > 500) spokenCache.clear()
        return spokenCache.getOrPut(line.nikud) {
            val fixed = SpokenText.fix(line.nikud)
            if (fixed == line.nikud) line.nikudTokens else LineTokens.of(fixed)
        }
    }

    /** Free text (messages, samples) as sent to the engine. */
    private fun spokenText(text: String): String =
        if (settings.speakNikud) SpokenText.fix(text) else HebrewText.stripNikud(text)

    private fun failPending(reason: String) {
        val (id, req) = pending ?: return
        pending = null
        _playback.value = Playback(id, req.tag, PlayStatus.ERROR, req.startLine, -1, reason)
    }

    private fun say(t: TextToSpeech, text: String, queueMode: Int, id: String): Boolean =
        runCatching { t.speak(text, queueMode, Bundle(), id) == TextToSpeech.SUCCESS }.getOrDefault(false)

    private fun uid(gen: Int, type: Char, line: Int, word: Int) = "$gen|$type|$line|$word"

    private fun parse(id: String): Uid? {
        val p = id.split('|')
        if (p.size != 4) return null
        val gen = p[0].toIntOrNull() ?: return null
        val type = p[1].firstOrNull() ?: return null
        val line = p[2].toIntOrNull() ?: return null
        val word = p[3].toIntOrNull() ?: return null
        return Uid(gen, type, line, word)
    }

    private fun isHebrew(locale: Locale?): Boolean {
        if (locale == null) return false
        val lang = locale.language.lowercase(Locale.ROOT)
        if (lang == "he" || lang == "iw") return true
        return runCatching { locale.isO3Language == "heb" }.getOrDefault(false)
    }

    private fun detectGender(v: Voice): String? {
        val text = (v.name + " " + v.features.orEmpty().joinToString(" ")).lowercase(Locale.ROOT)
        return when {
            "female" in text -> "f"
            Regex("(^|[^a-z])male").containsMatchIn(text) -> "m"
            Regex("smtf\\d").containsMatchIn(text) -> "f"
            Regex("smtm\\d").containsMatchIn(text) -> "m"
            else -> null
        }
    }

    companion object {
        private const val GOOGLE_TTS = "com.google.android.tts"

        /** Short vocalized sentence used to check whether a voice reports word positions. */
        const val PROBE_TEXT = "שָׁלוֹם לְכֻלָּם, הַיּוֹם נִקְרָא סִפּוּר יָפֶה וְנִשְׂמַח."
    }
}
