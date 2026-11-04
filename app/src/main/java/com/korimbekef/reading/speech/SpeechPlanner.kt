package com.korimbekef.reading.speech

/**
 * One piece of recorded audio to play: a whole line (highlighted with its word timings), part of a
 * line (from a word on, or a single word) or a separately recorded word.
 */
data class Segment(
    val file: String,
    /** Where to start, in ms of the recording. */
    val fromMs: Int = 0,
    /** Where to stop (null = the end of the recording). */
    val toMs: Int? = null,
    val line: Int,
    /** Word timings of a line recording; the highlighted word follows the playback position. */
    val timeline: Recording? = null,
    /** For single-word segments: the word that is heard. */
    val fixedWord: Int = -1,
    /** Silence after this segment, at normal speed. */
    val gapAfterMs: Long = 0,
) {
    fun wordAt(positionMs: Int): Int = timeline?.wordAt(positionMs) ?: fixedWord
}

/** Turns a [SpeakRequest] into recorded segments, or null when something has no recording. */
class SpeechPlanner(private val find: (String) -> Recording?) {

    fun plan(r: SpeakRequest): List<Segment>? {
        if (r.lines.isEmpty()) return null
        val start = r.startLine.coerceIn(0, r.lines.lastIndex)
        val end = r.endLine.coerceIn(start, r.lines.lastIndex)
        return when {
            r.singleWord -> singleWord(r, start)
            r.forceWordByWord -> wordByWord(r, start, end) ?: lines(r, start, end)
            else -> lines(r, start, end)
        }
    }

    private fun lines(r: SpeakRequest, start: Int, end: Int): List<Segment>? {
        val out = ArrayList<Segment>()
        for (li in start..end) {
            val line = r.lines[li]
            if (line.wordCount == 0) continue
            val rec = find(line.nikud) ?: return null
            val first = if (li == start) r.startWord.coerceIn(0, line.wordCount - 1) else 0
            val from = if (first > 0) (rec.words.getOrNull(first)?.startMs ?: 0) - LEAD_MS else 0
            out += Segment(rec.file, from.coerceAtLeast(0), null, li, timeline = rec, gapAfterMs = if (li < end) r.pauseBetweenLinesMs else 0)
        }
        return out.ifEmpty { null }
    }

    /** "Read together slowly": every word on its own, with a short pause, so the child can follow. */
    private fun wordByWord(r: SpeakRequest, start: Int, end: Int): List<Segment>? {
        val out = ArrayList<Segment>()
        for (li in start..end) {
            val tokens = r.lines[li].nikudTokens
            if (tokens.wordCount == 0) continue
            val first = if (li == start) r.startWord.coerceIn(0, tokens.wordCount - 1) else 0
            for (wi in first until tokens.wordCount) {
                val rec = find(tokens.speechWord(wi)) ?: return null
                val lastOfLine = wi == tokens.wordCount - 1
                val gap = when {
                    !lastOfLine -> WORD_GAP_MS
                    li < end -> r.pauseBetweenLinesMs
                    else -> 0
                }
                out += Segment(rec.file, line = li, fixedWord = wi, gapAfterMs = gap)
            }
        }
        return out.ifEmpty { null }
    }

    private fun singleWord(r: SpeakRequest, li: Int): List<Segment>? {
        val tokens = r.lines[li].nikudTokens
        if (tokens.wordCount == 0) return null
        val w = r.startWord.coerceIn(0, tokens.wordCount - 1)
        find(tokens.speechWord(w))?.let { return listOf(Segment(it.file, line = li, fixedWord = w)) }
        // no separate recording: cut the word out of the line recording
        val line = find(r.lines[li].nikud) ?: return null
        val t = line.words.getOrNull(w) ?: return null
        return listOf(Segment(line.file, (t.startMs - LEAD_MS).coerceAtLeast(0), t.endMs + TAIL_MS, li, fixedWord = w))
    }

    companion object {
        const val LEAD_MS = 40
        const val TAIL_MS = 90
        const val WORD_GAP_MS = 160L

        /** The recordings are made at a pace that matches this device-voice rate (a child's default). */
        const val RECORDED_PACE = 0.85f

        /** Playback speed of a recording for a reading rate chosen in the app. */
        fun speedFor(rate: Float): Float = (rate / RECORDED_PACE).coerceIn(0.5f, 1.8f)
    }
}
