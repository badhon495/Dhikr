package com.dhikr.app.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dhikr.app.MainActivity
import com.dhikr.app.R
import com.dhikr.app.core.prayer.Prayer
import com.dhikr.app.core.prayer.PrayerReminderScheduler

/**
 * Builds and posts the per-routine reminder notification, and owns the
 * notification channel plus the intent-extra / action constants shared by
 * [ReminderScheduler] and [ReminderReceiver].
 */
object ReminderNotifications {

    const val CHANNEL_ID = "reminders"
    const val EXTRA_ROUTINE_ID = "com.dhikr.app.extra.ROUTINE_ID"
    const val EXTRA_TASBIH_ID = "com.dhikr.app.extra.TASBIH_ID"
    const val EXTRA_IS_SNOOZE = "com.dhikr.app.extra.IS_SNOOZE"
    const val ACTION_SNOOZE = "com.dhikr.app.action.SNOOZE_REMINDER"

    /**
     * Stable notification / PendingIntent id for a reminder target. Routine and
     * tasbih ids live in the same int space, so the tasbih side is salted to
     * keep the two from colliding.
     */
    fun routineNotifId(routineId: String): Int = routineId.hashCode()
    fun tasbihNotifId(tasbihId: String): Int = ("tasbih:$tasbihId").hashCode()

    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notification_channel_name))
            .setDescription(context.getString(R.string.notification_channel_desc))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun post(context: Context, routineId: String, routineName: String) =
        post(context, routineNotifId(routineId), EXTRA_ROUTINE_ID, routineId, routineName)

    fun postTasbih(context: Context, tasbihId: String, tasbihName: String) =
        post(context, tasbihNotifId(tasbihId), EXTRA_TASBIH_ID, tasbihId, tasbihName)

    // hasPermission() is checked first; lint can't see through the helper.
    @SuppressLint("MissingPermission")
    private fun post(
        context: Context,
        id: Int,
        targetExtraKey: String,
        targetId: String,
        name: String,
    ) {
        if (!hasPermission(context)) return
        ensureChannel(context)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(targetExtraKey, targetId)
        }
        val openPending = PendingIntent.getActivity(context, id, openIntent, flags)

        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(targetExtraKey, targetId)
        }
        val snoozePending = PendingIntent.getBroadcast(context, id + 1, snoozeIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setLargeIcon(launcherBitmap(context))
            .setContentTitle(context.getString(R.string.reminder_notification_title, name))
            .setContentText(context.getString(R.string.reminder_notification_body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPending)
            .addAction(0, context.getString(R.string.reminder_snooze_action), snoozePending)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /**
     * "After <prayer>" reminder. Tapping opens [routineId] (the After Salah
     * routine) or, if that preset was deleted, just the app. No snooze: the
     * next prayer's reminder is never far away.
     */
    // hasPermission() is checked first; lint can't see through the helper.
    @SuppressLint("MissingPermission")
    fun postPrayer(context: Context, prayer: Prayer, routineId: String?) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val id = PrayerReminderScheduler.requestCode(prayer)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (routineId != null) putExtra(EXTRA_ROUTINE_ID, routineId)
        }
        val openPending = PendingIntent.getActivity(
            context, id, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val prayerName = context.getString(prayerNameRes(prayer))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setLargeIcon(launcherBitmap(context))
            .setContentTitle(context.getString(R.string.prayer_notification_title, prayerName))
            .setContentText(context.getString(R.string.prayer_notification_body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPending)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun prayerNameRes(prayer: Prayer): Int = when (prayer) {
        Prayer.FAJR -> R.string.prayer_fajr
        Prayer.DHUHR -> R.string.prayer_dhuhr
        Prayer.ASR -> R.string.prayer_asr
        Prayer.MAGHRIB -> R.string.prayer_maghrib
        Prayer.ISHA -> R.string.prayer_isha
    }

    fun cancel(context: Context, routineId: String) {
        NotificationManagerCompat.from(context).cancel(routineNotifId(routineId))
    }

    fun cancelTasbih(context: Context, tasbihId: String) {
        NotificationManagerCompat.from(context).cancel(tasbihNotifId(tasbihId))
    }

    /**
     * The full-colour launcher icon as a bitmap, for [NotificationCompat.Builder.setLargeIcon]
     * — shown in the expanded notification body. (The status-bar small icon
     * can only ever be a white silhouette.)
     */
    private fun launcherBitmap(context: Context): android.graphics.Bitmap? {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return null
        if (drawable is BitmapDrawable) return drawable.bitmap
        val size = drawable.intrinsicWidth.takeIf { it > 0 } ?: 108
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
