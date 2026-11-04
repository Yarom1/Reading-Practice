package com.korimbekef.reading.speech

import com.korimbekef.reading.core.DualText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechPlannerTest {

    private val line0 = DualText.parse("אַבָּא בָּא.")
    private val line1 = DualText.parse("אִמָּא בָּאָה, סַבָּא בָּא.")
    private val custom = DualText.ofUserText("שורה שאין לה הקלטה")
    private val lineRec0 = Recording("l0.mp3", 1500, listOf(WordTime(80, 600), WordTime(700, 1300)))
    private val lineRec1 = Recording("l1.mp3", 2600, listOf(WordTime(80, 500), WordTime(560, 1000), WordTime(1200, 1700), WordTime(1750, 2400)))
    private val recordings = mapOf(
        SpeechCatalog.key(line0.nikud) to lineRec0,
        SpeechCatalog.key(line1.nikud) to lineRec1,
        SpeechCatalog.key("אַבָּא") to Recording("w-aba.mp3", 700, listOf(WordTime(80, 520))),
        SpeechCatalog.key("בָּא") to Recording("w-ba.mp3", 500, listOf(WordTime(80, 330))),
    )
    private val planner = SpeechPlanner { recordings[SpeechCatalog.key(it)] }

    @Test
    fun wholeStoryPlaysEveryLineWithItsTimingsAndPauses() {
        val plan = planner.plan(SpeakRequest(listOf(line0, line1), startLine = 0, endLine = 1, pauseBetweenLinesMs = 450))!!
        assertEquals(listOf("l0.mp3", "l1.mp3"), plan.map { it.file })
        assertEquals(listOf(450L, 0L), plan.map { it.gapAfterMs })
        assertEquals(1, plan[0].wordAt(750))
        assertEquals(2, plan[1].wordAt(1250))
    }

    @Test
    fun startingFromAWordSeeksJustBeforeIt() {
        val plan = planner.plan(SpeakRequest(listOf(line0, line1), startLine = 1, startWord = 2))!!
        assertEquals(1, plan.size)
        assertEquals(1200 - SpeechPlanner.LEAD_MS, plan[0].fromMs)
        assertEquals(1, plan[0].line)
    }

    @Test
    fun singleWordUsesItsOwnRecordingOrIsCutFromTheLine() {
        val own = planner.plan(SpeakRequest(listOf(line0), startLine = 0, startWord = 0, singleWord = true))!!
        assertEquals("w-aba.mp3", own.single().file)
        assertEquals(0, own.single().fixedWord)
        // "סַבָּא," has no separate recording here: it is cut out of the line
        val cut = planner.plan(SpeakRequest(listOf(line0, line1), startLine = 1, startWord = 2, singleWord = true))!!
        assertEquals("l1.mp3", cut.single().file)
        assertEquals(1200 - SpeechPlanner.LEAD_MS, cut.single().fromMs)
        assertEquals(1700 + SpeechPlanner.TAIL_MS, cut.single().toMs)
        assertEquals(2, cut.single().wordAt(0))
    }

    @Test
    fun readTogetherSaysTheWordsOneByOneOrFallsBackToTheLine() {
        val words = planner.plan(SpeakRequest(listOf(line0), startLine = 0, forceWordByWord = true))!!
        assertEquals(listOf("w-aba.mp3", "w-ba.mp3"), words.map { it.file })
        assertEquals(listOf(0, 1), words.map { it.fixedWord })
        assertEquals(SpeechPlanner.WORD_GAP_MS, words[0].gapAfterMs)
        // line 1 has words without their own recording: the line recording is used instead
        val line = planner.plan(SpeakRequest(listOf(line0, line1), startLine = 1, forceWordByWord = true))!!
        assertEquals(listOf("l1.mp3"), line.map { it.file })
    }

    @Test
    fun textsWithoutRecordingGoToTheDeviceVoice() {
        assertNull(planner.plan(SpeakRequest(listOf(custom), startLine = 0)))
        assertNull(planner.plan(SpeakRequest(listOf(line0, custom), startLine = 0, endLine = 1)))
        assertNull(planner.plan(SpeakRequest(emptyList(), startLine = 0)))
    }

    @Test
    fun recordedSpeedFollowsTheChildsRate() {
        assertEquals(1f, SpeechPlanner.speedFor(SpeechPlanner.RECORDED_PACE), 0.001f)
        assertEquals(0.5f, SpeechPlanner.speedFor(0.1f), 0.001f)
    }
}
