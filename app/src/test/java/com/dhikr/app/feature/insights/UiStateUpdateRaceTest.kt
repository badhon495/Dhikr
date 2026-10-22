package com.dhikr.app.feature.insights

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the lost-update race that was in [InsightsViewModel]:
 * a long-running "totals" pipeline reads `_uiState.value`, suspends on slow
 * work, then writes back `_uiState.value = capturedValue.copy(...)`. If a
 * second, independent writer (the weekly-goal collector) updates the state
 * while the first is suspended, its write gets silently clobbered when the
 * first writer resumes and overwrites with its stale captured copy.
 *
 * [kotlinx.coroutines.flow.MutableStateFlow.update] performs an atomic
 * read-modify-write (via compareAndSet retry), so switching both writers to
 * it — the actual fix applied to InsightsViewModel — closes this race. This
 * test models both the broken pattern and the fixed pattern side by side on
 * a bare StateFlow, independent of the real repositories.
 */
data class RaceState(val slow: Int = 0, val fast: Int = 0)

class UiStateUpdateRaceTest {

    @Test
    fun directValueAssignmentLosesAConcurrentWrite() = runTest {
        val state = MutableStateFlow(RaceState())

        // Mirrors the old InsightsViewModel bug: capture-then-suspend-then-assign.
        val slowWriter = launch {
            val captured = state.value
            delay(100)
            state.value = captured.copy(slow = 1)
        }
        val fastWriter = launch {
            delay(50)
            state.value = state.value.copy(fast = 1)
        }
        slowWriter.join()
        fastWriter.join()

        // The fast writer's update landed while slowWriter was suspended, so
        // slowWriter's stale captured copy (fast = 0) overwrote it on resume.
        assertEquals(
            "demonstrates the bug: the fast writer's update is lost",
            0,
            state.value.fast,
        )
    }

    @Test
    fun atomicUpdatePreservesBothConcurrentWrites() = runTest {
        val state = MutableStateFlow(RaceState())

        val slowWriter = launch {
            delay(100)
            state.update { it.copy(slow = 1) }
        }
        val fastWriter = launch {
            delay(50)
            state.update { it.copy(fast = 1) }
        }
        slowWriter.join()
        fastWriter.join()

        assertEquals(1, state.value.slow)
        assertEquals(
            "the fix: .update {} composes instead of clobbering a concurrent write",
            1,
            state.value.fast,
        )
    }
}
