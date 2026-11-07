package com.dhikr.app.core.backup

import java.util.concurrent.TimeUnit

/** Decides whether an automatic backup is due. */
object AutoBackupPolicy {

    const val INTERVAL_HOURS = 24L

    fun isDue(enabled: Boolean, nowMillis: Long, lastBackupAt: Long): Boolean {
        if (!enabled) return false
        return lastBackupAt <= 0L || nowMillis - lastBackupAt >= TimeUnit.HOURS.toMillis(INTERVAL_HOURS)
    }
}
