package com.korimbekef.reading.core

import java.text.Normalizer

/**
 * The text that is sent to the speech engine. Nikud has no separate sign for the short "o" of
 * qamats qatan, so engines read כָּל as "kal". Such words are sent with holam male instead
 * (כָּל → כּוֹל, לְכָל → לְכוֹל) so they are said "kol". The screen keeps the original spelling, and
 * the words stay the same in number and order, so highlighting positions remain valid.
 */
object SpokenText {
    private const val QAMATS = 'ָ'
    private const val QAMATS_QATAN = 'ׇ'
    private const val HOLAM = 'ֹ'
    private const val PREFIX_LETTERS = "ובלמשכה"

    /** Whole words (any prefixes allowed before them) that are read with o instead of a. */
    private val EXACT = listOf("אָזְנַיִם" to "אוֹזְנַיִם").map { (a, b) -> nfc(a) to nfc(b) }

    fun fix(text: String): String {
        if (text.none { it == QAMATS || it == QAMATS_QATAN }) return text
        val sb = StringBuilder(text.length + 8)
        var i = 0
        while (i < text.length) {
            if (text[i].isWhitespace()) {
                sb.append(text[i])
                i++
                continue
            }
            val s = i
            while (i < text.length && !text[i].isWhitespace()) i++
            sb.append(fixToken(text.substring(s, i)))
        }
        return sb.toString()
    }

    private fun fixToken(token: String): String {
        var a = 0
        var b = token.length
        while (a < b && !HebrewText.isWordCore(token[a])) a++
        while (b > a && !HebrewText.isWordCore(token[b - 1])) b--
        if (a >= b) return token
        val core = token.substring(a, b)
        val fixed = fixWord(core)
        return if (fixed == core) token else token.substring(0, a) + fixed + token.substring(b)
    }

    fun fixWord(word: String): String {
        val w = nfc(word)
        for ((from, to) in EXACT) {
            if (w.endsWith(from) && HebrewText.lettersOnly(w.dropLast(from.length)).all { it in PREFIX_LETTERS }) {
                return w.dropLast(from.length) + to
            }
        }
        // כָּל with any prefixes (בְּכָל, לְכָל, וּבְכָל...): the qamats of the כ is a qamats qatan
        val letters = HebrewText.lettersOnly(w)
        if (!letters.endsWith("כל") || letters.length > 5 || !letters.dropLast(2).all { it in PREFIX_LETTERS }) return word
        val k = w.lastIndexOf('כ')
        val l = w.indexOf('ל', k + 1)
        if (k < 0 || l < 0) return word
        val marks = w.substring(k + 1, l)
        if (QAMATS !in marks && QAMATS_QATAN !in marks) return word
        return w.substring(0, k + 1) + marks.filter { it != QAMATS && it != QAMATS_QATAN } + "ו" + HOLAM + w.substring(l)
    }

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)
}
