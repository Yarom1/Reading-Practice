package com.korimbekef.reading.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HebrewTextTest {

    @Test
    fun stripsNikudButKeepsLettersAndPunctuation() {
        assertEquals("שלום, דני!", HebrewText.stripNikud("שָׁלוֹם, דָּנִי!"))
        assertTrue(HebrewText.hasNikud("שָׁלוֹם"))
        assertFalse(HebrewText.hasNikud("שלום"))
    }

    @Test
    fun tokenizesWordsWithNikudAndPunctuation() {
        val t = LineTokens.of("דָּנִי יָצָא לְטַיֵּל בַּגִּנָּה.")
        assertEquals(4, t.wordCount)
        assertEquals("דָּנִי", t.word(0))
        assertEquals("בַּגִּנָּה.", t.word(3))
        assertEquals("בַּגִּנָּה", t.speechWord(3))
    }

    @Test
    fun standalonePunctuationAttachesToPreviousWord() {
        val t = LineTokens.of("אַבָּא — אִמָּא")
        assertEquals(2, t.wordCount)
        assertEquals("אַבָּא —", t.word(0))
        assertEquals("אִמָּא", t.word(1))
    }

    @Test
    fun mapsCharacterOffsetsToWords() {
        val text = "אַבָּא בָּא הַבַּיְתָה"
        val t = LineTokens.of(text)
        assertEquals(0, t.wordIndexAt(0))
        assertEquals(0, t.wordIndexAt(3))
        assertEquals(1, t.wordIndexAt(text.indexOf(" בָּא") + 1))
        assertEquals(2, t.wordIndexAt(text.length - 1))
        assertEquals(2, t.wordIndexAt(text.length + 10))
    }

    @Test
    fun nikudAndPlainVersionsAlign() {
        val d = DualText.parse("דָּנִי {לְטַיֵּל|לטייל} בַּגִּנָּה.")
        assertEquals("דָּנִי לְטַיֵּל בַּגִּנָּה.", d.nikud)
        assertEquals("דני לטייל בגינה.".replace("בגינה", "בגנה"), d.plain)
        assertEquals(d.nikudTokens.wordCount, d.plainTokens.wordCount)
    }

    @Test
    fun fullAlternativeSyntax() {
        val d = DualText.parse("הַצִּפּוֹר שָׁרָה || הציפור שרה")
        assertEquals("הציפור שרה", d.plain)
    }

    @Test
    fun firstLetterUsesBaseForm() {
        assertEquals('ש', HebrewText.firstLetter("שֶׁמֶשׁ"))
        assertEquals('כ', HebrewText.baseLetter('ך'))
    }

    @Test
    fun normalizeCollapsesSpaces() {
        assertEquals("אַבָּא בָּא", HebrewText.normalize("  אַבָּא   בָּא "))
    }
}
