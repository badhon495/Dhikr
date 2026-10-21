package com.dhikr.app.core.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * One inexact alarm per enabled prayer, at [PrayerSettings.offsetMinutes]
 * past its next occurrence. Prayer times move every day, so each alarm only
 * covers the next occurrence and [PrayerReminderReceiver] re-arms it after
 * firing — same pattern (and same no-exact-alarm-permission trade-off) as
 * ReminderScheduler.
 */
class PrayerReminderScheduler(private val appContext: Context) {

    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(prayer: Prayer): PendingIntent {
        val intent = Intent(appContext, PrayerReminderReceiver::class.java).apply {
            putExtra(PrayerReminderReceiver.EXTRA_PRAYER, prayer.name)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode(prayer),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Arms (or cancels) every prayer's alarm to match [settings]. */
    fun apply(settings: PrayerSettings, nowMillis: Long = System.currentTimeMillis()) {
        Prayer.entries.forEach { schedule(it, settings, nowMillis) }
    }

    fun schedule(prayer: Prayer, settings: PrayerSettings, nowMillis: Long = System.currentTimeMillis()) {
        val lat = settings.latitude
        val lng = settings.longitude
        val at = if (lat != null && lng != null && settings.isEnabled(prayer)) {
            PrayerTimes.nextOccurrence(
                prayer, nowMillis, settings.offsetMinutes, lat, lng, settings.method, settings.asr,
            )
        } else {
            null
        }
        if (at == null) {
            alarmManager.cancel(pendingIntent(prayer))
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(prayer))
        }
    }

    companion object {
        /** Request / notification id; salted so it can't collide with routine ids. */
        fun requestCode(prayer: Prayer): Int = ("prayer:${prayer.name}").hashCode()
    }
}
