package com.dhikr.app.core.backup

import java.util.concurrent.TimeUnit

/**
 * When Home gently reminds the user to back up: they have history worth
 * losing, the last backup (file or Drive) is older than [STALE_DAYS] or never
 * happened, and they haven't tapped "Later" recently.
 */
object BackupNudge {

    const val STALE_DAYS = 14
    const val SNOOZE_DAYS = 7

    private val DAY_MILLIS = TimeUnit.DAYS.toMillis(1)

    fun shouldShow(nowMillis: Long, lastBackupAt: Long, snoozedUntil: Long, hasHistory: Boolean): Boolean {
        if (!hasHistory) return false
        if (nowMillis < snoozedUntil) return false
        return lastBackupAt <= 0L || nowMillis - lastBackupAt >= STALE_DAYS * DAY_MILLIS
    }

    fun snoozeUntil(nowMillis: Long): Long = nowMillis + SNOOZE_DAYS * DAY_MILLIS
}
