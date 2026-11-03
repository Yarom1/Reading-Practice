package com.korimbekef.reading

import com.korimbekef.reading.speech.EffectiveSync
import com.korimbekef.reading.speech.EngineInfo
import com.korimbekef.reading.speech.EngineStatus
import com.korimbekef.reading.speech.Playback
import com.korimbekef.reading.speech.PlayStatus
import com.korimbekef.reading.speech.RangeSupport
import com.korimbekef.reading.speech.SpeakRequest
import com.korimbekef.reading.speech.SpeechController
import com.korimbekef.reading.speech.SpeechState
import com.korimbekef.reading.speech.VoiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Application used by Robolectric tests: identical to the real one, but with a scripted voice. */
class TestApp : ReadingApp() {
    override fun createSpeechController(container: AppContainer): SpeechController =
        FakeSpeechController().also { fake = it }

    companion object {
        lateinit var fake: FakeSpeechController
    }
}

/**
 * A speech engine the test drives by hand: [speak] starts a request, [emitWord] moves the
 * highlight and [finish] completes it — exactly the events the real engine produces.
 */
class FakeSpeechController : SpeechController {
    val requests = mutableListOf<Pair<Long, SpeakRequest>>()
    val messages = mutableListOf<String>()
    var stops = 0
        private set
    private var counter = 0L
    private var current: Pair<Long, SpeakRequest>? = null

    private val _state = MutableStateFlow(
        SpeechState(
            status = EngineStatus.READY,
            engines = listOf(EngineInfo("com.google.android.tts", "Speech Services by Google")),
            enginePackage = "com.google.android.tts",
            voices = listOf(
                VoiceInfo("he-il-x-heb-local", "קול 1", isNetwork = false, quality = 400, gender = "f"),
                VoiceInfo("he-il-x-heb-network", "קול 2", isNetwork = true, quality = 500, gender = null),
            ),
            currentVoice = "he-il-x-heb-local",
            rangeSupport = RangeSupport.SUPPORTED,
            effectiveSync = EffectiveSync.ENGINE_RANGES,
        ),
    )
    private val _playback = MutableStateFlow(Playback())
    override val state: StateFlow<SpeechState> = _state
    override val playback: StateFlow<Playback> = _playback

    val isSpeaking: Boolean get() = _playback.value.status == PlayStatus.SPEAKING

    override fun speak(request: SpeakRequest): Long {
        val id = ++counter
        requests += id to request
        current = id to request
        _playback.value = Playback(id, request.tag, PlayStatus.SPEAKING, request.startLine, -1)
        return id
    }

    fun emitWord(line: Int, word: Int) {
        val (id, r) = current ?: return
        _playback.value = Playback(id, r.tag, PlayStatus.SPEAKING, line, word)
    }

    fun finish() {
        val (id, r) = current ?: return
        current = null
        _playback.value = Playback(id, r.tag, PlayStatus.DONE, r.endLine, -1)
    }

    override fun pause() {
        val pb = _playback.value
        if (pb.status == PlayStatus.SPEAKING) _playback.value = pb.copy(status = PlayStatus.PAUSED)
    }

    override fun resume() {
        val pb = _playback.value
        if (pb.status == PlayStatus.PAUSED) _playback.value = pb.copy(status = PlayStatus.SPEAKING)
    }

    override fun stop() {
        stops++
        current = null
        val pb = _playback.value
        if (pb.status == PlayStatus.SPEAKING || pb.status == PlayStatus.PAUSED) {
            _playback.value = pb.copy(status = PlayStatus.IDLE, word = -1)
        }
    }

    override fun speakMessage(text: String, rate: Float) {
        messages += text
    }

    override fun preview(voiceName: String?, text: String, rate: Float) {
        messages += text
    }

    override fun recheckSync() = Unit
}
