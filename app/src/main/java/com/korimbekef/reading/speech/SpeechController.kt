package com.korimbekef.reading.speech

import com.korimbekef.reading.core.DualText
import kotlinx.coroutines.flow.StateFlow

enum class EngineStatus {
    INITIALIZING,
    READY,

    /** An engine exists but it has no Hebrew voice (or the voice data is not installed). */
    NO_HEBREW,

    /** No text-to-speech engine is installed or it failed to start. */
    NO_ENGINE,
}

/** Whether the current voice reports word positions while speaking (needed for one-utterance sync). */
enum class RangeSupport {
    UNKNOWN,

    /** Word positions refer to the text we sent (with nikud). */
    SUPPORTED,

    /** Word positions refer to the text without nikud, so we send plain text in that mode. */
    SUPPORTED_PLAIN,

    UNSUPPORTED,
}

/** The highlighting strategy actually used for a request. */
enum class EffectiveSync { ENGINE_RANGES, WORD_BY_WORD }

enum class PlayStatus { IDLE, SPEAKING, PAUSED, DONE, ERROR }

data class VoiceInfo(
    val name: String,
    val label: String,
    val isNetwork: Boolean,
    val quality: Int,
    /** "m", "f" or null when the engine does not say. */
    val gender: String?,
)

data class EngineInfo(val packageName: String, val label: String)

data class SpeechState(
    /** Whether the app can speak at all: READY when the recorded voice or a device voice works. */
    val status: EngineStatus = EngineStatus.INITIALIZING,
    /** The app's own recorded voice is available and switched on (built-in texts use it). */
    val recorded: Boolean = false,
    /** The device text-to-speech engine, used for texts that have no recording (e.g. a parent's story). */
    val deviceStatus: EngineStatus = status,
    val engines: List<EngineInfo> = emptyList(),
    val enginePackage: String? = null,
    val voices: List<VoiceInfo> = emptyList(),
    val currentVoice: String? = null,
    val rangeSupport: RangeSupport = RangeSupport.UNKNOWN,
    val effectiveSync: EffectiveSync = EffectiveSync.WORD_BY_WORD,
    /** A short notice for the parent area (e.g. "switched to an offline voice"). */
    val notice: String? = null,
)

/**
 * What to read. Lines are given in both versions; the engine speaks the version chosen in the
 * settings (with nikud by default, for better pronunciation) while the screen may show either —
 * word indexes are identical in both versions.
 */
data class SpeakRequest(
    val lines: List<DualText>,
    val startLine: Int,
    val endLine: Int = startLine,
    val startWord: Int = 0,
    val rate: Float = 1f,
    val tag: String = TAG_LINE,
    val forceWordByWord: Boolean = false,
    /** Speak only the word [startWord] of [startLine]. */
    val singleWord: Boolean = false,
    val pauseBetweenLinesMs: Long = 450,
) {
    companion object {
        const val TAG_LINE = "line"
        const val TAG_ALL = "all"
        const val TAG_WORD = "word"
        const val TAG_TOGETHER = "together"
    }
}

/** Live playback position used for highlighting. [word] is -1 when no word is being spoken. */
data class Playback(
    val requestId: Long = 0,
    val tag: String = "",
    val status: PlayStatus = PlayStatus.IDLE,
    val line: Int = -1,
    val word: Int = -1,
    val error: String? = null,
)

interface SpeechController {
    val state: StateFlow<SpeechState>
    val playback: StateFlow<Playback>

    /** Stops whatever is playing and starts [request]. Returns the request id. */
    fun speak(request: SpeakRequest): Long

    fun pause()
    fun resume()
    fun stop()

    /** Speaks an encouragement or instruction; not highlighted. */
    fun speakMessage(text: String, rate: Float = 1f)

    /** Plays a sample with [voiceName] without changing the saved voice. */
    fun preview(voiceName: String?, text: String, rate: Float)

    /** Re-checks whether the current voice supports synced word positions. */
    fun recheckSync()

    /** How long [text] takes to say at [rate], when that is known in advance (recorded texts). */
    fun estimatedDurationMs(text: String, rate: Float): Long? = null

    companion object {
        /** Voice name for [preview] that plays the app's recorded voice instead of a device voice. */
        const val RECORDED_VOICE = "app:noa"
    }
}
