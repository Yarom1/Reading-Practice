package com.korimbekef.reading.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Normalizer

class SpokenTextTest {
    private fun n(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    @Test
    fun kolIsReadWithHolam() {
        assertEquals(n("כּוֹל הַכָּבוֹד!"), SpokenText.fix(n("כָּל הַכָּבוֹד!")))
        assertEquals(n("לְכוֹל הַצְּדָדִים"), SpokenText.fix(n("לְכָל הַצְּדָדִים")))
        assertEquals(n("וּבְכוֹל עֶרֶב,"), SpokenText.fix(n("וּבְכָל עֶרֶב,")))
        assertEquals(n("הַחוֹל אוֹזְנַיִם"), SpokenText.fix(n("הַחוֹל אָזְנַיִם")))
    }

    @Test
    fun otherWordsStayAsTheyAre() {
        for (w in listOf("הַכֹּל", "אָכַל", "אָכְלָה", "כֻּלָּם", "מַאֲכָל", "הָלְכָה", "שָׁלוֹם")) {
            assertEquals(w, n(w), SpokenText.fix(n(w)))
        }
    }

    @Test
    fun wordsKeepTheirCountSoHighlightingStaysAligned() {
        val line = n("עַכְשָׁיו קְרָא אַתָּה אֶת כָּל הַסִּפּוּר!")
        assertEquals(LineTokens.of(line).wordCount, LineTokens.of(SpokenText.fix(line)).wordCount)
    }
}
