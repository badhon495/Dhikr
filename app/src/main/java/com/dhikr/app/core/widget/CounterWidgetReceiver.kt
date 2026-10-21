package com.dhikr.app.core.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dhikr.app.core.counter.LiveCounter
import com.dhikr.app.core.counter.WidgetCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles the counter widget's [+] tap: applies one increment to the persisted
 * session off the main thread, then refreshes both widgets. exported=false —
 * only our own PendingIntent fires it.
 */
class CounterWidgetReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INCREMENT) return
        val pending = goAsync()
        scope.launch {
            try {
                val live = LiveCounter.handle
                if (live != null) {
                    // The Counter screen still holds this session in memory and
                    // would overwrite a store-level increment on its next save
                    // (see LiveCounter), so count in the live ViewModel instead
                    // and refresh once its debounced persist has landed.
                    withContext(Dispatchers.Main) { live.tap() }
                    delay(LIVE_PERSIST_SETTLE_MS)
                } else {
                    WidgetCounter.applyIncrement(context)
                }
                DhikrWidgets.refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_INCREMENT = "com.dhikr.app.action.WIDGET_INCREMENT"

        // CounterViewModel debounces session saves by 500ms.
        private const val LIVE_PERSIST_SETTLE_MS = 700L
    }
}
