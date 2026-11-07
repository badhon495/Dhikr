package com.dhikr.app.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AutoBackupPolicyTest {
    private val day = 24L * 60 * 60 * 1000

    @get:Rule val tmp = TemporaryFolder()

    @Test fun disabledNeverDue() {
        assertFalse(AutoBackupPolicy.isDue(false, day * 10, 0))
    }

    @Test fun dueWhenNeverBackedUpOrStale() {
        assertTrue(AutoBackupPolicy.isDue(true, day * 10, 0))
        assertTrue(AutoBackupPolicy.isDue(true, day * 10, day * 9))
    }

    @Test fun notDueWhenRecent() {
        assertFalse(AutoBackupPolicy.isDue(true, day * 10, day * 10 - 1000))
    }

    @Test fun localWriteKeepsNewestThree() {
        val dir = tmp.newFolder("b")
        repeat(5) { LocalAutoBackup.write(dir, "{\"n\":$it}", day * (it + 1)) }
        val files = LocalAutoBackup.list(dir)
        assertEquals(3, files.size)
        assertEquals("{\"n\":4}", files.first().readText())
    }
}
