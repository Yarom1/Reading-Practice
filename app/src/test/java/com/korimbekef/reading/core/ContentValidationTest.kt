package com.korimbekef.reading.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the authored reading content (assets/content):
 *  - both versions of every line have the same words (so highlighting maps 1:1),
 *  - nikud marks are well formed (one vowel per letter, shin/sin dots, nothing on final letters…),
 *  - the plain spelling differs from the vocalized one only by added ו / י (ktiv male).
 */
class ContentValidationTest {

    private fun asset(name: String): String {
        val candidates = listOf("src/main/assets/content/$name", "app/src/main/assets/content/$name")
        val file = candidates.map { File(it) }.firstOrNull { it.exists() } ?: error("missing asset $name")
        return file.readText()
    }

    private val passages by lazy { ContentParser.parsePassages(asset("passages.json")) }
    private val vocabulary by lazy { ContentParser.parseVocabulary(asset("vocabulary.json")) }

    private fun allTexts(): List<Pair<String, DualText>> =
        passages.flatMap { p ->
            listOf("${p.id}/title" to p.title) + p.lines.mapIndexed { i, l -> "${p.id}/${i + 1}" to l }
        } + vocabulary.flatMap { v ->
            listOfNotNull("vocab/${v.word.plain}" to v.word, v.description?.let { "vocab/${v.word.plain}/d" to it })
        }

    @Test
    fun levelsAndCounts() {
        assertTrue("expected 30-50 passages, got ${passages.size}", passages.size in 30..50)
        for (level in 1..5) {
            val n = passages.count { it.level == level }
            assertTrue("level $level has only $n passages", n >= 6)
        }
        assertEquals(passages.size, passages.map { it.id }.toSet().size)
        assertTrue(vocabulary.size >= 60)
        assertEquals("duplicate vocabulary", vocabulary.size, vocabulary.map { it.plainLetters }.toSet().size)
        passages.forEach { assertTrue("${it.id} has no lines", it.lines.isNotEmpty()) }
    }

    @Test
    fun nikudAndPlainHaveTheSameWords() {
        val problems = allTexts().mapNotNull { (where, t) ->
            if (t.nikudTokens.wordCount != t.plainTokens.wordCount) "$where: ${t.nikud} | ${t.plain}" else null
        }
        assertTrue("word count mismatch:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun plainTextHasNoMarksAndNoBraces() {
        val problems = allTexts().mapNotNull { (where, t) ->
            when {
                HebrewText.hasNikud(t.plain) -> "$where: nikud in plain: ${t.plain}"
                t.nikud.any { it in "{}|" } || t.plain.any { it in "{}|" } -> "$where: unparsed markup: ${t.nikud}"
                else -> null
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun plainSpellingOnlyAddsVavOrYod() {
        val problems = mutableListOf<String>()
        for ((where, t) in allTexts()) {
            for (i in 0 until t.wordCount) {
                val stripped = HebrewText.stripNikud(t.nikudTokens.word(i))
                val plain = t.plainTokens.word(i)
                if (stripped == plain) continue
                fun core(s: String) = s.filter { it != 'ו' && it != 'י' }
                if (core(stripped) != core(plain)) problems += "$where word ${i + 1}: $stripped -> $plain"
            }
        }
        assertTrue("suspicious plain spellings:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun nikudMarksAreWellFormed() {
        val problems = mutableListOf<String>()
        for ((where, t) in allTexts()) {
            val s = t.nikud
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (HebrewText.isMark(c)) {
                    problems += "$where: mark without letter at $i in \"$s\""
                    i++
                    continue
                }
                if (!HebrewText.isHebrewLetter(c)) {
                    i++
                    continue
                }
                var j = i + 1
                var vowels = 0
                var dagesh = 0
                var dots = 0
                while (j < s.length && HebrewText.isMark(s[j])) {
                    val m = s[j]
                    when {
                        m in HebrewText.VOWELS -> vowels++
                        m == HebrewText.DAGESH -> dagesh++
                        m == HebrewText.SHIN_DOT || m == HebrewText.SIN_DOT -> dots++
                    }
                    j++
                }
                val word = s.substring(maxOf(0, i - 6), minOf(s.length, j + 6))
                if (vowels > 1) problems += "$where: two vowels on $c … $word"
                if (dagesh > 1) problems += "$where: double dagesh … $word"
                if (c == 'ש' && dots != 1) problems += "$where: shin without exactly one dot … $word"
                if (c != 'ש' && dots > 0) problems += "$where: shin dot on $c … $word"
                if (c in "םןףץ" && vowels > 0) problems += "$where: vowel on final $c … $word"
                i = j
            }
            // every word with 2+ letters should carry some vowel (catches forgotten nikud)
            for (w in 0 until t.wordCount) {
                val word = t.nikudTokens.word(w)
                val letters = word.count { HebrewText.isHebrewLetter(it) }
                if (letters >= 2 && word.none { it in HebrewText.VOWELS || it == HebrewText.DAGESH }) {
                    problems += "$where: no nikud in \"$word\""
                }
            }
        }
        assertTrue("nikud problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** Not a failure: lists words that may need a {nikud|plain} override, for human review. */
    @Test
    fun reportPossibleMissingOverrides() {
        val suspects = sortedSetOf<String>()
        for ((_, t) in allTexts()) {
            for (i in 0 until t.wordCount) {
                val nikud = t.nikudTokens.word(i)
                val stripped = HebrewText.stripNikud(nikud)
                if (stripped != t.plainTokens.word(i)) continue
                if (needsPlene(nikud)) suspects += HebrewText.trimPunctuation(nikud)
            }
        }
        println("POSSIBLE MISSING KTIV-MALE OVERRIDES (${suspects.size}):")
        suspects.forEach { println("  $it") }
    }

    private fun needsPlene(word: String): Boolean {
        for (i in word.indices) {
            val c = word[i]
            if (c == HebrewText.QUBUTS) return true
            if (c == HebrewText.HOLAM) {
                // holam on a letter other than vav, not followed by alef/he (לֹא, פֹּה …)
                val base = word.substring(0, i).lastOrNull { HebrewText.isHebrewLetter(it) }
                val next = word.substring(i + 1).firstOrNull { HebrewText.isHebrewLetter(it) }
                if (base != 'ו' && next != 'א' && next != 'ה' && next != 'ו') return true
            }
            if (c == HebrewText.HATAF_QAMATS) return true
        }
        return false
    }
}
