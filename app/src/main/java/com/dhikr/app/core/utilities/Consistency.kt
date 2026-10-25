package com.dhikr.app.core.utilities

/**
 * Consistency run (plan.md §18): consecutive days with any dhikr, counted back
 * from today. Today not counted yet doesn't break the run — the day isn't
 * over — so it counts back from yesterday instead.
 *
 * One grace day: a single missed day between two active days is bridged (it
 * isn't counted, it just doesn't break the run), at most once per
 * [GRACE_WINDOW_DAYS]. Two missed days in a row, or a second miss inside the
 * window, start a fresh run. Nothing here ever reports a "lost" run.
 */
object Consistency {

    /** Milestones from plan.md §18, celebrated gently. */
    val MILESTONES = listOf(7, 15, 23, 30, 100)

    /** A bridged miss is allowed once per this many days. */
    const val GRACE_WINDOW_DAYS = 7

    data class Summary(
        val currentRun: Int,
        val bestRun: Int,
        /** Highest milestone the current run has reached, or null. */
        val reachedMilestone: Int?,
        /** Next milestone ahead of the current run, or null past the last. */
        val nextMilestone: Int?,
    )

    /**
     * @param activeDayStarts local-midnight starts of every day with a
     *   count > 0, in any order.
     */
    fun summarize(activeDayStarts: Collection<Long>, todayStart: Long, dayMillis: Long): Summary {
        val days = activeDayStarts.toHashSet()
        val window = GRACE_WINDOW_DAYS * dayMillis

        var cursor = if (todayStart in days) todayStart else todayStart - dayMillis
        var run = 0
        var lastBridge: Long? = null
        while (true) {
            if (cursor in days) {
                run++
            } else if ((cursor - dayMillis) in days && (lastBridge == null || lastBridge - cursor >= window)) {
                lastBridge = cursor
            } else {
                break
            }
            cursor -= dayMillis
        }

        var best = 0
        var streak = 0
        var previous: Long? = null
        var bridge: Long? = null
        for (day in days.sorted()) {
            val gap = previous?.let { day - it }
            streak = when {
                gap == dayMillis -> streak + 1
                gap == 2 * dayMillis && (bridge == null || (day - dayMillis) - bridge >= window) -> {
                    bridge = day - dayMillis
                    streak + 1
                }
                else -> {
                    bridge = null
                    1
                }
            }
            if (streak > best) best = streak
            previous = day
        }
        return Summary(
            currentRun = run,
            bestRun = best,
            reachedMilestone = MILESTONES.lastOrNull { it <= run },
            nextMilestone = MILESTONES.firstOrNull { it > run },
        )
    }
}
