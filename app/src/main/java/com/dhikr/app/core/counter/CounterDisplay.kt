package com.dhikr.app.core.counter

/**
 * The "x of y" a session shows outside the counter screen (Home's continue
 * card, the widget, the Quick Settings tile), matching what the counter
 * screen itself shows. A dhikr with lapTarget 1 counts whole laps — its
 * in-lap count is always 0 — so it shows laps done out of the lap goal.
 */
object CounterDisplay {

    fun progress(count: Int, lap: Int, lapTarget: Int, lapCount: Int): Pair<Int, Int> =
        if (lapTarget <= 1) (lap - 1).coerceAtLeast(0) to lapCount else count to lapTarget
}
