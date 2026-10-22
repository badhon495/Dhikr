package com.dhikr.app.core.counter

import org.junit.Assert.assertEquals
import org.junit.Test

class CounterDisplayTest {

    @Test
    fun oneTapDhikrShowsLapsDone() {
        // lapTarget 1, goal of 3 laps, tapped 9 times: engine is on lap 10.
        assertEquals(9 to 3, CounterDisplay.progress(count = 0, lap = 10, lapTarget = 1, lapCount = 3))
    }

    @Test
    fun freshOneTapDhikrShowsZero() {
        assertEquals(0 to 1, CounterDisplay.progress(count = 0, lap = 1, lapTarget = 1, lapCount = 1))
    }

    @Test
    fun ordinaryDhikrShowsCountInLap() {
        assertEquals(12 to 33, CounterDisplay.progress(count = 12, lap = 2, lapTarget = 33, lapCount = 3))
    }
}
