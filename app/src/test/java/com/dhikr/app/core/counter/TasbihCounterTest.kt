package com.dhikr.app.core.counter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TasbihCounterTest {

    @Test
    fun undoDuringBonusLapsKeepsCompleteTrue() {
        // lapTarget=3, totalLaps=1: the 3rd tap rolls lap 1 -> 2 and fires the
        // one-shot "just completed" signal; isComplete is sticky from then on.
        val counter = TasbihCounter(lapTarget = 3, totalLaps = 1)
        counter.increment() // count 1
        counter.increment() // count 2
        val completing = counter.increment() // rolls to lap 2, complete=true
        assertTrue(completing.isComplete)

        // Keep tapping into bonus laps (lap 2, well past totalLaps=1).
        counter.increment() // count 1
        counter.increment() // count 2
        counter.increment() // rolls to lap 3

        // Undo one tap while still deep in bonus laps (lap 3 > totalLaps 1).
        val afterUndo = counter.undo()

        assertTrue(
            "undo while still past the goal must not clear the completion flag",
            afterUndo.isComplete,
        )
    }

    @Test
    fun undoBackBelowTheGoalClearsComplete() {
        // lapTarget=3, totalLaps=1.
        val counter = TasbihCounter(lapTarget = 3, totalLaps = 1)
        counter.increment() // count 1
        counter.increment() // count 2, history has (0,1) then (1,1)
        val completing = counter.increment() // rolls to lap 2, complete=true
        assertTrue(completing.isComplete)

        // Undo the exact tap that reached the goal: back to count=2, lap=1.
        val afterUndo = counter.undo()

        assertEquals(2, afterUndo.count)
        assertEquals(1, afterUndo.lap)
        assertFalse(
            "undo back below the goal boundary must clear the completion flag",
            afterUndo.isComplete,
        )
    }

    @Test
    fun undoWithEmptyHistoryIsANoOpSnapshot() {
        val counter = TasbihCounter(lapTarget = 33, totalLaps = 3)
        val snapshot = counter.undo()
        assertEquals(0, snapshot.count)
        assertEquals(1, snapshot.lap)
        assertFalse(snapshot.isComplete)
    }
}
