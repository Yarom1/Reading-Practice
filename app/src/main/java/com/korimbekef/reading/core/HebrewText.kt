package com.korimbekef.reading.core

import java.text.Normalizer

/** Helpers for vocalized (with nikud) and plain Hebrew text. Pure Kotlin, no Android deps. */
object HebrewText {
    private const val MAQAF = '־'
    private const val PASEQ = '׀'
    private const val SOF_PASUQ = '׃'
    private const val NUN_HAFUKHA = '׆'

    const val SHEVA = 'ְ'
    const val HATAF_SEGOL = 'ֱ'
    const val HATAF_PATAH = 'ֲ'
    const val HATAF_QAMATS = 'ֳ'
    const val HIRIQ = 'ִ'
    const val TSERE = 'ֵ'
    const val SEGOL = 'ֶ'
    const val PATAH = 'ַ'
    const val QAMATS = 'ָ'
    const val HOLAM = 'ֹ'
    const val HOLAM_HASER_FOR_VAV = 'ֺ'
    const val QUBUTS = 'ֻ'
    const val DAGESH = 'ּ'
    const val SHIN_DOT = 'ׁ'
    const val SIN_DOT = 'ׂ'
    const val QAMATS_QATAN = 'ׇ'

    val VOWELS: Set<Char> = setOf(
        SHEVA, HATAF_SEGOL, HATAF_PATAH, HATAF_QAMATS, HIRIQ, TSERE, SEGOL,
        PATAH, QAMATS, HOLAM, HOLAM_HASER_FOR_VAV, QUBUTS, QAMATS_QATAN,
    )

    /** Hebrew points (nikud) and cantillation marks — everything that is removed in plain text. */
    fun isMark(c: Char): Boolean =
        c in '֑'..'ׇ' && c != MAQAF && c != PASEQ && c != SOF_PASUQ && c != NUN_HAFUKHA

    fun isHebrewLetter(c: Char): Boolean = c in 'א'..'ת'

    fun isFinalLetter(c: Char): Boolean = c == 'ך' || c == 'ם' || c == 'ן' || c == 'ף' || c == 'ץ'

    fun hasNikud(text: String): Boolean = text.any { isMark(it) }

    fun stripNikud(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) if (!isMark(c)) sb.append(c)
        return sb.toString()
    }

    /** Canonical form used everywhere: NFC, single spaces, trimmed. */
    fun normalize(text: String): String {
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
        return nfc.replace(' ', ' ')
            .replace('\t', ' ')
            .replace(Regex(" {2,}"), " ")
            .trim()
    }

    /** Letters, digits and marks are the "core" of a word; punctuation around them is not. */
    fun isWordCore(c: Char): Boolean = c.isLetterOrDigit() || isMark(c)

    /** Removes punctuation at both ends of a word ("שָׁלוֹם," -> "שָׁלוֹם"). */
    fun trimPunctuation(word: String): String {
        var s = 0
        var e = word.length
        while (s < e && !isWordCore(word[s])) s++
        while (e > s && !isWordCore(word[e - 1])) e--
        return word.substring(s, e)
    }

    /** Only the Hebrew letters of a word (no marks, punctuation, geresh) — used for lookups. */
    fun lettersOnly(word: String): String = buildString {
        for (c in word) if (isHebrewLetter(c) || c.isLetterOrDigit() && !isMark(c)) append(c)
    }

    /** Converts a final letter to its regular form (ך→כ) for "first letter" style comparisons. */
    fun baseLetter(c: Char): Char = when (c) {
        'ך' -> 'כ'
        'ם' -> 'מ'
        'ן' -> 'נ'
        'ף' -> 'פ'
        'ץ' -> 'צ'
        else -> c
    }

    fun firstLetter(word: String): Char? = word.firstOrNull { isHebrewLetter(it) }?.let { baseLetter(it) }
}

/** A word inside a line: [start, end) character offsets. */
data class WordSpan(val start: Int, val end: Int)

/**
 * Splits a line into words. A "word" is a whitespace-separated run that contains at least one
 * letter or digit; stand-alone punctuation (like a dash) is attached to the previous word so it
 * never becomes a highlight target of its own.
 */
class LineTokens private constructor(val text: String, val words: List<WordSpan>) {

    val wordCount: Int get() = words.size

    fun word(index: Int): String = words[index].let { text.substring(it.start, it.end) }

    /** The word as it should be sent to a speech engine on its own (no surrounding punctuation). */
    fun speechWord(index: Int): String = HebrewText.trimPunctuation(word(index))

    /** Index of the word that contains (or precedes) [offset]; 0 for offsets before the first word. */
    fun wordIndexAt(offset: Int): Int {
        if (words.isEmpty()) return -1
        var lo = 0
        var hi = words.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid].start <= offset) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    /** True when [offset] is exactly the first character of some word. */
    fun isWordStart(offset: Int): Boolean = words.any { it.start == offset }

    companion object {
        fun of(text: String): LineTokens {
            val spans = ArrayList<WordSpan>()
            var pendingStart = -1
            var i = 0
            while (i < text.length) {
                if (text[i].isWhitespace()) {
                    i++
                    continue
                }
                val s = i
                while (i < text.length && !text[i].isWhitespace()) i++
                val e = i
                var hasCore = false
                for (k in s until e) {
                    if (text[k].isLetterOrDigit()) {
                        hasCore = true
                        break
                    }
                }
                if (hasCore) {
                    spans.add(WordSpan(if (pendingStart >= 0) pendingStart else s, e))
                    pendingStart = -1
                } else if (spans.isNotEmpty()) {
                    spans[spans.lastIndex] = spans.last().copy(end = e)
                } else if (pendingStart < 0) {
                    pendingStart = s
                }
            }
            return LineTokens(text, spans)
        }
    }
}
