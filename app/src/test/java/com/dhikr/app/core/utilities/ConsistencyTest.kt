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
