package com.dhikr.app.core.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dhikr.app.DhikrApplication
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.notifications.ReminderNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires shortly after a prayer: posts the "after <prayer>" notification
 * (opening the After Salah routine when it still exists) and re-arms that
 * prayer's next occurrence.
 */
class PrayerReminderReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val prayer = Prayer.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_PRAYER) } ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val settings = AppPreferencesRepository(context.applicationContext).prayerSettings.first()
                if (settings.hasLocation && settings.isEnabled(prayer)) {
                    val app = context.applicationContext as DhikrApplication
                    val routineId = AFTER_SALAH_ROUTINE_ID
                        .takeIf { app.database.routineDao().getRoutineRaw(it) != null }
                    ReminderNotifications.postPrayer(context, prayer, routineId)
                }
                // Re-arm (or cancel, if the reminder was switched off meanwhile).
                PrayerReminderScheduler(context.applicationContext).schedule(prayer, settings)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PRAYER = "com.dhikr.app.extra.PRAYER"
        /** Preset routine seeded in SeedData. */
        const val AFTER_SALAH_ROUTINE_ID = "after_salah"
    }
}
