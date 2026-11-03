package com.korimbekef.reading.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressRulesTest {

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        val days = setOf(10L, 11L, 12L, 14L)
        assertEquals(1, Streaks.current(days, today = 14))
        assertEquals(1, Streaks.current(days, today = 15))
        assertEquals(3, Streaks.current(days, today = 13))
        assertEquals(0, Streaks.current(days, today = 20))
        assertEquals(3, Streaks.best(days))
        assertEquals(0, Streaks.best(emptySet()))
    }

    @Test
    fun passageStarsCapAtThree() {
        assertEquals(0, Rewards.passageStars(0))
        assertEquals(2, Rewards.passageStars(2))
        assertEquals(3, Rewards.passageStars(9))
    }

    @Test
    fun mascotStageFollowsStars() {
        assertEquals(MascotStage.BABY, MascotStage.forStars(0))
        assertEquals(MascotStage.GLASSES, MascotStage.forStars(25))
        assertEquals(MascotStage.CROWN, MascotStage.forStars(5000))
        assertNull(MascotStage.CROWN.next)
    }

    @Test
    fun medalsAreEarnedFromStats() {
        val none = Medals.earnedCodes(ChildStats())
        assertTrue(none.isEmpty())
        val some = Medals.earnedCodes(ChildStats(linesRead = 60, passagesCompleted = 5, bestStreak = 3))
        assertTrue("first_line" in some)
        assertTrue("stories_5" in some)
        assertTrue("lines_50" in some)
        assertTrue("streak_3" in some)
        assertTrue("streak_7" !in some)
    }

    @Test
    fun levelAdvisorNeedsMostStoriesOfTheLevel() {
        assertNull(LevelAdvisor.recommendedLevel(level = 1, completedInLevel = 2, totalInLevel = 8))
        assertEquals(2, LevelAdvisor.recommendedLevel(level = 1, completedInLevel = 5, totalInLevel = 8))
        assertNull(LevelAdvisor.recommendedLevel(level = 5, completedInLevel = 8, totalInLevel = 8))
    }

    @Test
    fun pinHashing() {
        val salt = PinHasher.newSalt()
        val a = PinHasher.hash("1234", salt)
        assertEquals(a, PinHasher.hash("1234", salt))
        assertNotEquals(a, PinHasher.hash("1235", salt))
        assertTrue(PinHasher.isValidPin("0007"))
        assertTrue(!PinHasher.isValidPin("12a4"))
    }
}
