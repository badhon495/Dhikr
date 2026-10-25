package com.dhikr.app.core.backup

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupNudgeTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 100 * day

    @Test
    fun noHistory_neverNudges() {
        assertFalse(BackupNudge.shouldShow(now, lastBackupAt = 0L, snoozedUntil = 0L, hasHistory = false))
    }

    @Test
    fun neverBackedUpWithHistory_nudges() {
        assertTrue(BackupNudge.shouldShow(now, lastBackupAt = 0L, snoozedUntil = 0L, hasHistory = true))
    }

    @Test
    fun recentBackup_doesNotNudge() {
        assertFalse(BackupNudge.shouldShow(now, now - 13 * day, 0L, true))
    }

    @Test
    fun staleBackup_nudges() {
        assertTrue(BackupNudge.shouldShow(now, now - 14 * day, 0L, true))
    }

    @Test
    fun snoozed_hidesUntilItExpires() {
        val snooze = BackupNudge.snoozeUntil(now)
        assertFalse(BackupNudge.shouldShow(now + day, 0L, snooze, true))
        assertTrue(BackupNudge.shouldShow(snooze, 0L, snooze, true))
        assertEquals(now + 7 * day, snooze)
    }
}
