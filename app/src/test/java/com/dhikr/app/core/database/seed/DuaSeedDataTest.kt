package com.dhikr.app.core.database.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuaSeedDataTest {
    @Test
    fun seedData_has20UniqueEntriesAllValidAndSourced() {
        val entries = DuaSeedData.all
        assertEquals(20, entries.size)
        assertEquals(20, entries.map { it.id }.toSet().size) // unique ids
        entries.forEach { entry ->
            assertTrue("${entry.id} missing nameBn", !entry.nameBn.isNullOrBlank())
            assertTrue("${entry.id} missing translationBn", !entry.translationBn.isNullOrBlank())
            assertTrue("${entry.id} missing source", !entry.source.isNullOrBlank())
            assertTrue("${entry.id} missing arabic", entry.arabic.isNotBlank())
        }
    }

    @Test
    fun seedData_excludesAlreadyCoveredWakingUp() {
        assertTrue(DuaSeedData.all.none { it.id == "wake_alhamdu" || it.category == "waking" })
    }
}
