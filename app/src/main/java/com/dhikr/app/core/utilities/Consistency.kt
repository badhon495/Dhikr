package com.dhikr.app.core.utilities

/**
 * Consistency run (plan.md §18): consecutive days with any dhikr, counted back
 * from today. Today not counted yet doesn't break the run — the day isn't
 * over — so it counts back from yesterday instead. Nothing here ever reports
 * a "lost" run; a gap simply means the next run starts fresh.
 */
object Consistency {

    /** Milestones from plan.md §18, celebrated gently. */
    val MILESTONES = listOf(7, 15, 23, 30, 100)

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
        var cursor = if (todayStart in days) todayStart else todayStart - dayMillis
        var run = 0
        while (cursor in days) {
            run++
            cursor -= dayMillis
        }
        var best = 0
        var streak = 0
        var previous: Long? = null
        for (day in days.sorted()) {
            streak = if (previous != null && day - previous == dayMillis) streak + 1 else 1
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
