package com.dhikr.app.core.counter

/**
 * Process-wide hook to the Counter screen's in-memory session, for counting
 * surfaces that live outside the Activity (the Quick Settings tile).
 *
 * While CounterScreen is in composition, CounterViewModel's engine state is
 * the source of truth and its own debounced persist would overwrite anything
 * written to the session store underneath it — so an out-of-app tap must be
 * routed into the live ViewModel instead of through [WidgetCounter]. Null
 * whenever no Counter screen exists; callers then fall back to the store.
 */
object LiveCounter {

    class Handle(
        /** Counts one tap in the live session. Main thread only. */
        val tap: () -> Unit,
        /** Current tasbih name and "count/target" line, for display. */
        val status: () -> Status,
    )

    data class Status(val title: String, val subtitle: String)

    @Volatile
    var handle: Handle? = null
}
