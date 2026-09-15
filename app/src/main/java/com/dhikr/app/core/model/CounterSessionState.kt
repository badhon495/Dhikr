package com.dhikr.app.core.model

data class CounterSessionState(
    val activeDhikrId: String,
    val count: Int,
    val lap: Int,
    val previousCount: Int?,
    val previousLap: Int?,
    val running: Boolean,
    val elapsedSeconds: Int,
    val locked: Boolean,
    val routineId: String?,
    val routineStep: Int,
    // Cumulative count (across laps) already written to permanent History for
    // this session. Every history log records only `totalCount - loggedTotal`
    // so a session that is left and re-entered without new taps records
    // nothing the second time — see CounterViewModel.logCurrentSessionIfNonZero().
    val loggedTotal: Int = 0,
    // Local midnight this slot was last saved under (DayBounds.startOfTodayMillis()
    // at persist() time). This one global slot has no per-day expiry of its own
    // (unlike routine_progress / tasbih_progress, which get filtered by day at
    // the query), so a session left mid-routine yesterday would otherwise resume
    // at yesterday's exact step/count once the day-scoped progress row for it is
    // gone. Callers must check this against today's start before trusting the
    // count/lap/routineStep fields as a resume fallback. Defaults to 0 for rows
    // written before this field existed, which correctly reads as "stale".
    val dayStartMillis: Long = 0L,
) {
    companion object {
        fun fresh(dhikrId: String) = CounterSessionState(
            activeDhikrId = dhikrId,
            count = 0,
            lap = 1,
            previousCount = null,
            previousLap = null,
            running = true,
            elapsedSeconds = 0,
            locked = false,
            routineId = null,
            routineStep = 0,
            loggedTotal = 0,
            dayStartMillis = 0L,
        )
    }
}
