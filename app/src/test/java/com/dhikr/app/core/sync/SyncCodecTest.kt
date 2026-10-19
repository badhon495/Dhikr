package com.dhikr.app.core.sync

import com.dhikr.app.core.backup.BackupFile
import com.dhikr.app.core.backup.BackupPreferences
import com.dhikr.app.core.backup.BackupTasbih
import com.dhikr.app.core.backup.SYNC_FORMAT
import com.dhikr.app.core.backup.SYNC_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SyncCodecTest {

    private val sample = BackupFile(
        customTasbih = listOf(
            BackupTasbih(
                id = "a", name = "A", arabic = "x", translation = "t",
                lapTarget = 33, lapCount = 3, createdAt = 1, updatedAt = 2,
            ),
        ),
        preferences = BackupPreferences(dailyGoalTarget = 700),
    )

    private fun kindOf(text: String): SyncException.Kind {
        try {
            SyncCodec.decode(text)
        } catch (e: SyncException) {
            return e.kind
        }
        fail("expected SyncException")
        throw AssertionError()
    }

    @Test
    fun roundTripPreservesData() {
        val decoded = SyncCodec.decode(SyncCodec.encode(sample))
        assertEquals(sample.customTasbih, decoded.customTasbih)
        assertEquals(700, decoded.preferences.dailyGoalTarget)
        assertEquals(SYNC_FORMAT, decoded.format)
    }

    @Test
    fun rejectsWrongFormat() {
        val manualBackup = """{"format":"dhikr.backup","version":1}"""
        assertEquals(SyncException.Kind.CORRUPT, kindOf(manualBackup))
    }

    @Test
    fun rejectsNewerVersion() {
        val newer = """{"format":"$SYNC_FORMAT","version":${SYNC_VERSION + 1}}"""
        assertEquals(SyncException.Kind.NEWER_FORMAT, kindOf(newer))
    }

    @Test
    fun rejectsGarbage() {
        assertEquals(SyncException.Kind.CORRUPT, kindOf("not json at all"))
    }

    @Test
    fun ignoresUnknownKeys() {
        val text = """{"format":"$SYNC_FORMAT","version":$SYNC_VERSION,"futureField":42}"""
        assertEquals(emptyList<BackupTasbih>(), SyncCodec.decode(text).customTasbih)
    }
}
