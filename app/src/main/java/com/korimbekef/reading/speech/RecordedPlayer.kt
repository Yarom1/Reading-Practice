package com.korimbekef.reading.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/**
 * Plays recorded segments one after another (assets/speech). The highlighted word comes from the
 * real playback position and the word timings of the recording, checked every few milliseconds,
 * so it stays exact at any speed. Everything runs on the main thread.
 */
class RecordedPlayer(context: Context) {

    interface Listener {
        fun onWord(line: Int, word: Int)
        fun onDone()
        fun onError(reason: String, line: Int, word: Int)
    }

    private val assets = context.applicationContext.assets
    private val main = Handler(Looper.getMainLooper())

    private var generation = 0
    private var segments: List<Segment> = emptyList()
    private var index = 0
    private var speed = 1f
    private var listener: Listener? = null
    private var player: MediaPlayer? = null
    private var playing = false
    private var paused = false
    private var waitingForGap = false
    private var lastLine = -1
    private var lastWord = -1

    val isActive: Boolean get() = listener != null

    /** Line and word heard last (for pause / fallback). */
    val position: Pair<Int, Int> get() = lastLine to lastWord

    fun play(segments: List<Segment>, speed: Float, listener: Listener) {
        stop()
        this.segments = segments
        this.speed = speed.coerceIn(0.5f, 2f)
        this.listener = listener
        index = 0
        lastLine = segments.firstOrNull()?.line ?: -1
        lastWord = -1
        start(generation)
    }

    fun pause(): Boolean {
        if (listener == null || paused) return false
        paused = true
        main.removeCallbacksAndMessages(null)
        if (playing) runCatching { player?.pause() }
        return true
    }

    fun resume(): Boolean {
        if (listener == null || !paused) return false
        paused = false
        val gen = generation
        when {
            waitingForGap -> next(gen)
            playing -> {
                runCatching { player?.start() }
                tick(gen)
            }
            // still preparing: onPrepared will start it
        }
        return true
    }

    fun stop() {
        generation++
        main.removeCallbacksAndMessages(null)
        release()
        listener = null
        paused = false
        waitingForGap = false
    }

    // ------------------------------------------------------------------ internals

    private fun start(gen: Int) {
        if (gen != generation) return
        val seg = segments.getOrNull(index) ?: return finish(gen)
        release()
        waitingForGap = false
        val mp = MediaPlayer()
        player = mp
        try {
            assets.openFd("speech/${seg.file}").use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mp.setOnPreparedListener { onPrepared(gen, it, seg) }
            mp.setOnCompletionListener { if (gen == generation && player === it) segmentEnded(gen) }
            mp.setOnErrorListener { p, what, extra ->
                if (gen == generation && player === p) fail(gen, "media-$what-$extra")
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            fail(gen, "open-${seg.file}")
        }
    }

    private fun onPrepared(gen: Int, mp: MediaPlayer, seg: Segment) {
        if (gen != generation || player !== mp) return
        if (seg.fromMs > 0) {
            mp.setOnSeekCompleteListener { if (gen == generation && player === it) begin(gen, it, seg) }
            runCatching { mp.seekTo(seg.fromMs.toLong(), MediaPlayer.SEEK_CLOSEST) }.onFailure { begin(gen, mp, seg) }
        } else {
            begin(gen, mp, seg)
        }
    }

    private fun begin(gen: Int, mp: MediaPlayer, seg: Segment) {
        if (gen != generation) return
        playing = true
        if (seg.timeline == null && seg.fixedWord >= 0) emit(seg.line, seg.fixedWord)
        if (paused) {
            // paused while preparing: keep the position, start on resume
            return
        }
        val withSpeed = runCatching { mp.playbackParams = mp.playbackParams.setSpeed(speed).setPitch(1f) }.isSuccess
        runCatching { if (!withSpeed || !mp.isPlaying) mp.start() }
        tick(gen)
    }

    private fun tick(gen: Int) {
        if (gen != generation || paused) return
        val mp = player ?: return
        val seg = segments.getOrNull(index) ?: return
        val pos = runCatching { mp.currentPosition }.getOrDefault(0)
        val word = seg.wordAt(pos)
        if (word >= 0) emit(seg.line, word)
        val stopAt = seg.toMs
        if (stopAt != null && pos >= stopAt) {
            runCatching { mp.pause() }
            segmentEnded(gen)
            return
        }
        main.postDelayed({ tick(gen) }, TICK_MS)
    }

    private fun segmentEnded(gen: Int) {
        if (gen != generation) return
        main.removeCallbacksAndMessages(null)
        val seg = segments.getOrNull(index)
        release()
        index++
        if (index >= segments.size) return finish(gen)
        val gap = ((seg?.gapAfterMs ?: 0L) / speed).toLong()
        waitingForGap = true
        if (!paused) main.postDelayed({ next(gen) }, gap)
    }

    private fun next(gen: Int) {
        if (gen != generation || paused) return
        start(gen)
    }

    private fun finish(gen: Int) {
        if (gen != generation) return
        val l = listener
        stop()
        l?.onDone()
    }

    private fun fail(gen: Int, reason: String) {
        if (gen != generation) return
        val l = listener
        val (line, word) = position
        stop()
        l?.onError(reason, line, word)
    }

    private fun emit(line: Int, word: Int) {
        if (line == lastLine && word == lastWord) return
        lastLine = line
        lastWord = word
        listener?.onWord(line, word)
    }

    private fun release() {
        playing = false
        player?.let { mp ->
            runCatching { mp.setOnCompletionListener(null) }
            runCatching { mp.release() }
        }
        player = null
    }

    companion object {
        private const val TICK_MS = 25L
    }
}
