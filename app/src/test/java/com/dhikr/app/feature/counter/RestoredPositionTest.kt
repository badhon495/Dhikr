package com.dhikr.app.feature.counter

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoredPositionTest {

    @Test
    fun bonusLapsPastTheGoalSurviveRestore() {
        // 1-per-lap dhikr with a 1-lap goal, counted 5 times: the engine is on
        // lap 6. A restore after process death must come back on lap 6, not 2.
        assertEquals(0 to 6, restoredPosition(count = 0, lap = 6, lapTarget = 1))
    }

    @Test
    fun countIsKeptInsideTheLap() {
        assertEquals(32 to 3, restoredPosition(count = 40, lap = 3, lapTarget = 33))
    }

    @Test
    fun corruptValuesFallBackToStart() {
        assertEquals(0 to 1, restoredPosition(count = -4, lap = 0, lapTarget = 33))
    }
}
