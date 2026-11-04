package com.korimbekef.reading.speech

import android.content.Context
import com.korimbekef.reading.data.prefs.AppSettings
import com.korimbekef.reading.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The app's voice: everything that has a recording (all built-in texts) is played from
 * assets/speech, with exact word timings; anything else (a parent's own story) goes to the
 * device's text-to-speech. Both report through the same [playback] flow.
 */
class HybridSpeechController(
    context: Context,
    private val settingsRepo: SettingsRepository,
    private val scope: CoroutineScope,
    private val device: SpeechController,
) : SpeechController {

    private val appContext = context.applicationContext
    private val player = RecordedPlayer(appContext)

    @Volatile private var manifestLoaded: SpeechManifest? = null
    private val manifest: SpeechManifest
        get() = manifestLoaded ?: synchronized(this) { manifestLoaded ?: load().also { manifestLoaded = it } }
    private val planner = SpeechPlanner { manifest.find(it) }

    private val _playback = MutableStateFlow(Playback())
    private val _state = MutableStateFlow(SpeechState())
    override val playback: StateFlow<Playback> = _playback.asStateFlow()
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var settings = AppSettings()
    private var counter = 0L

    /** The request currently playing (or paused): its id and who plays it. */
    private var current: Current? = null

    private data class Current(val id: Long, val request: SpeakRequest, val deviceId: Long = -1, val recorded: Boolean)

    private val recordedOn: Boolean get() = settings.useRecordedVoice && manifest.size > 0

    init {
        scope.launch(Dispatchers.IO) { manifest }
        scope.launch {
            combine(settingsRepo.settings, device.state) { s, d -> s to d }.collect { (s, d) ->
                settings = s
                val recorded = s.useRecordedVoice && (manifestLoaded?.size ?: 0) > 0
                _state.value = d.copy(
                    status = if (recorded) EngineStatus.READY else d.status,
                    deviceStatus = d.status,
                    recorded = recorded,
                )
            }
        }
        scope.launch { device.playback.collect { onDevicePlayback(it) } }
    }

    private fun load(): SpeechManifest = runCatching {
        appContext.assets.open("speech/manifest.json").bufferedReader().use { SpeechManifest.parse(it.readText()) }
    }.getOrDefault(SpeechManifest.EMPTY).also { m ->
        scope.launch {
            val recorded = settings.useRecordedVoice && m.size > 0
            _state.value = _state.value.copy(
                recorded = recorded,
                status = if (recorded) EngineStatus.READY else _state.value.deviceStatus,
            )
        }
    }

    // ------------------------------------------------------------------ requests

    override fun speak(request: SpeakRequest): Long {
        val id = ++counter
        stopAll()
        val segments = if (recordedOn) planner.plan(request) else null
        if (segments != null) {
            current = Current(id, request, recorded = true)
            _playback.value = Playback(id, request.tag, PlayStatus.SPEAKING, segments.first().line, -1)
            player.play(segments, speedFor(request.rate), listenerFor(id, request))
        } else {
            startOnDevice(id, request)
        }
        return id
    }

    private fun startOnDevice(id: Long, request: SpeakRequest) {
        current = Current(id, request, deviceId = -2, recorded = false)
        val deviceId = device.speak(request)
        if (current?.id == id) current = current?.copy(deviceId = deviceId)
        // the device may already have answered (e.g. an error) before its id was known
        onDevicePlayback(device.playback.value)
    }

    private fun listenerFor(id: Long, request: SpeakRequest) = object : RecordedPlayer.Listener {
        override fun onWord(line: Int, word: Int) {
            if (current?.id != id) return
            _playback.value = Playback(id, request.tag, PlayStatus.SPEAKING, line, word)
        }

        override fun onDone() {
            if (current?.id != id) return
            current = null
            val last = request.endLine.coerceIn(request.startLine, (request.lines.size - 1).coerceAtLeast(request.startLine))
            _playback.value = Playback(id, request.tag, PlayStatus.DONE, if (request.singleWord) request.startLine else last, -1)
        }

        override fun onError(reason: String, line: Int, word: Int) {
            if (current?.id != id) return
            // a broken recording should not leave the child without a voice: continue on the device
            if (_state.value.deviceStatus == EngineStatus.READY) {
                val from = if (line >= 0) request.copy(startLine = line, startWord = word.coerceAtLeast(0)) else request
                startOnDevice(id, from)
            } else {
                current = null
                _playback.value = Playback(id, request.tag, PlayStatus.ERROR, line, -1, "recorded-$reason")
            }
        }
    }

    private fun onDevicePlayback(pb: Playback) {
        val c = current ?: return
        if (c.recorded || c.deviceId < 0 || pb.requestId != c.deviceId) return
        _playback.value = pb.copy(requestId = c.id)
        if (pb.status == PlayStatus.DONE || pb.status == PlayStatus.ERROR) current = null
    }

    override fun pause() {
        val c = current ?: return
        if (c.recorded) {
            if (player.pause()) {
                val (line, word) = player.position
                _playback.value = _playback.value.copy(status = PlayStatus.PAUSED, line = line, word = word)
            }
        } else {
            device.pause()
        }
    }

    override fun resume() {
        val c = current ?: return
        if (c.recorded) {
            if (player.resume()) _playback.value = _playback.value.copy(status = PlayStatus.SPEAKING)
        } else {
            device.resume()
        }
    }

    override fun stop() {
        val wasActive = current != null
        stopAll()
        val pb = _playback.value
        if (wasActive && (pb.status == PlayStatus.SPEAKING || pb.status == PlayStatus.PAUSED)) {
            _playback.value = pb.copy(status = PlayStatus.IDLE, word = -1)
        }
    }

    /** Silences both voices (a recorded message or a device message may be playing too). */
    private fun stopAll() {
        current = null
        player.stop()
        device.stop()
    }

    override fun speakMessage(text: String, rate: Float) {
        val rec = if (recordedOn) manifest.find(text) else null
        if (rec == null) {
            if (current?.recorded == true) stop()
            player.stop()
            device.speakMessage(text, rate)
            return
        }
        stop()
        player.play(listOf(Segment(rec.file, line = -1)), speedFor(rate), silentListener)
    }

    private val silentListener = object : RecordedPlayer.Listener {
        override fun onWord(line: Int, word: Int) = Unit
        override fun onDone() = Unit
        override fun onError(reason: String, line: Int, word: Int) = Unit
    }

    override fun preview(voiceName: String?, text: String, rate: Float) {
        if (voiceName == SpeechController.RECORDED_VOICE) {
            val rec = manifest.find(text) ?: return
            stop()
            player.play(listOf(Segment(rec.file, line = -1)), speedFor(rate), silentListener)
        } else {
            stop()
            device.preview(voiceName, text, rate)
        }
    }

    override fun recheckSync() = device.recheckSync()

    override fun estimatedDurationMs(text: String, rate: Float): Long? {
        if (!recordedOn) return null
        val rec = manifest.find(text) ?: return null
        return (rec.durationMs / speedFor(rate)).toLong()
    }

    private fun speedFor(rate: Float) = SpeechPlanner.speedFor(rate)
}
