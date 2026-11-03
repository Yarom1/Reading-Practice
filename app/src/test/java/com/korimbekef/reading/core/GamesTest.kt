package com.korimbekef.reading.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class GamesTest {
    private fun asset(name: String): String =
        listOf("src/main/assets/content/$name", "app/src/main/assets/content/$name").map { File(it) }.first { it.exists() }.readText()

    private val vocab = ContentParser.parseVocabulary(asset("vocabulary.json"))
    private val passages = ContentParser.parsePassages(asset("passages.json"))

    @Test
    fun everyQuestionHasOneCorrectDistinctOption() {
        val generator = GameGenerator(vocab, Random(7))
        for (type in GameType.entries.filter { it != GameType.MEMORY }) {
            val questions = generator.questions(type, 8, passages = passages.filter { it.level <= 2 })
            assertEquals("$type should produce 8 questions", 8, questions.size)
            for (q in questions) {
                assertEquals(3, q.options.size)
                assertTrue(q.correctIndex in q.options.indices)
                val keys = q.options.map { it.emoji ?: it.letter?.toString() ?: it.text!!.plain }
                assertEquals("duplicate options in $type: $keys", keys.size, keys.toSet().size)
            }
        }
    }

    @Test
    fun missingWordDistractorsComeFromOtherPassages() {
        val generator = GameGenerator(vocab, Random(3))
        val level1 = passages.filter { it.level == 1 }
        val questions = generator.questions(GameType.MISSING_WORD, 8, passages = level1)
        for (q in questions) {
            val line = q.promptText!!
            val passage = level1.first { p -> p.lines.contains(line) }
            val own = passage.lines.flatMap { l -> (0 until l.wordCount).map { HebrewText.lettersOnly(l.plainTokens.speechWord(it)) } }.toSet()
            q.options.forEachIndexed { i, o ->
                if (i != q.correctIndex) assertTrue("distractor from same passage", HebrewText.lettersOnly(o.text!!.plain) !in own)
            }
        }
    }

    @Test
    fun memoryCardsComeInPairs() {
        val cards = GameGenerator(vocab, Random(1)).memoryCards(6)
        assertEquals(12, cards.size)
        assertTrue(cards.groupBy { it.pairId }.values.all { pair -> pair.size == 2 && pair.count { it.emoji != null } == 1 })
    }
}
