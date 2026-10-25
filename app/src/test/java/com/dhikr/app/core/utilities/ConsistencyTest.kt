package com.dhikr.app.core.utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsistencyTest {

    private val day = 86_400_000L
    private val today = 100 * day

    private fun days(vararg agoes: Int) = agoes.map { today - it * day }

    @Test
    fun countsBackFromToday() {
        val s = Consistency.summarize(days(0, 1, 2, 3, 4, 5, 6), today, day)
        assertEquals(7, s.currentRun)
        assertEquals(7, s.reachedMilestone)
        assertEquals(15, s.nextMilestone)
    }

    @Test
    fun todayNotYetCounted_keepsYesterdaysRun() {
        val s = Consistency.summarize(days(1, 2, 3), today, day)
        assertEquals(3, s.currentRun)
        assertNull(s.reachedMilestone)
        assertEquals(7, s.nextMilestone)
    }

    @Test
    fun gapStartsFresh_butBestRunIsRemembered() {
        val s = Consistency.summarize(days(0, 5, 6, 7, 8), today, day)
        assertEquals(1, s.currentRun)
        assertEquals(4, s.bestRun)
    }

    @Test
    fun singleMissedDayIsBridged() {
        val s = Consistency.summarize(days(0, 1, 3, 4), today, day)
        assertEquals(4, s.currentRun)
        assertEquals(4, s.bestRun)
    }

    @Test
    fun yesterdayMissedWhileTodayPending_isBridged() {
        val s = Consistency.summarize(days(2, 3, 4), today, day)
        assertEquals(3, s.currentRun)
    }

    @Test
    fun twoMissedDaysInARowBreakTheRun() {
        val s = Consistency.summarize(days(0, 1, 4, 5), today, day)
        assertEquals(2, s.currentRun)
        assertEquals(2, s.bestRun)
    }

    @Test
    fun secondMissInsideWindowBreaksTheRun() {
        // misses on day 1 and day 3 ago: two bridges inside 7 days.
        val s = Consistency.summarize(days(0, 2, 4, 5), today, day)
        assertEquals(2, s.currentRun)
    }

    @Test
    fun graceRefreshesAfterWindow() {
        // misses 1 and 9 days ago: 8 days apart, both bridged.
        val s = Consistency.summarize(days(0, 2, 3, 4, 5, 6, 7, 8, 10), today, day)
        assertEquals(9, s.currentRun)
    }

    @Test
    fun noActivity() {
        val s = Consistency.summarize(emptyList(), today, day)
        assertEquals(0, s.currentRun)
        assertEquals(0, s.bestRun)
        assertEquals(7, s.nextMilestone)
    }

    @Test
    fun pastLastMilestone_hasNoNext() {
        val s = Consistency.summarize((0 until 120).map { today - it * day }, today, day)
        assertEquals(100, s.reachedMilestone)
        assertNull(s.nextMilestone)
    }
}
