package com.dhikr.app.core.fasting

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dhikr.app.MainActivity
import com.dhikr.app.R
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.notifications.NextReminderTime
import com.dhikr.app.core.notifications.ReminderNotifications
import com.dhikr.app.core.utilities.FastReason
import com.dhikr.app.core.utilities.HijriCalendar
import com.dhikr.app.core.utilities.IslamicSeasons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Optional evening-before reminder for Sunnah fasts. One inexact alarm at
 * [CHECK_MINUTE_OF_DAY] every day; the receiver posts only when tomorrow is a
 * fast day ([IslamicSeasons.fastReason]) and always re-arms for the next
 * evening.
 */
object FastingReminder {

    /** 20:00 — after Maghrib in most places, before most people sleep. */
    const val CHECK_MINUTE_OF_DAY = 20 * 60
    private const val REQUEST_CODE = 0x5A57 // "fast"; distinct from hashed reminder ids

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, FastingReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun apply(context: Context, enabled: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context.applicationContext)
        if (enabled) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                NextReminderTime.next(System.currentTimeMillis(), CHECK_MINUTE_OF_DAY, 0),
                pi,
            )
        } else {
            alarmManager.cancel(pi)
        }
    }

    // hasPermission() is checked first; lint can't see through the helper.
    @SuppressLint("MissingPermission")
    fun post(context: Context, reason: FastReason) {
        if (!ReminderNotifications.hasPermission(context)) return
        ReminderNotifications.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = when (reason) {
            FastReason.ARAFAH -> R.string.fasting_reason_arafah
            FastReason.ASHURA -> R.string.fasting_reason_ashura
            FastReason.WHITE_DAYS -> R.string.fasting_reason_white_days
            FastReason.MONDAY -> R.string.fasting_reason_monday
            FastReason.THURSDAY -> R.string.fasting_reason_thursday
        }
        val notification = NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(context.getString(R.string.fasting_notification_title))
            .setContentText(context.getString(body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(REQUEST_CODE, notification)
    }
}

class FastingReminderReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                val prefs = AppPreferencesRepository(context.applicationContext)
                if (!prefs.fastingReminderEnabled.first()) return@launch
                val tomorrow = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
                val hijri = runCatching { HijriCalendar.of(tomorrow, prefs.hijriAdjustDays.first()) }.getOrNull()
                val dow = Calendar.getInstance().apply { timeInMillis = tomorrow }.get(Calendar.DAY_OF_WEEK)
                hijri?.let { IslamicSeasons.fastReason(it, dow) }?.let { FastingReminder.post(context, it) }
                FastingReminder.apply(context, enabled = true)
            } finally {
                pending.finish()
            }
        }
    }
}
