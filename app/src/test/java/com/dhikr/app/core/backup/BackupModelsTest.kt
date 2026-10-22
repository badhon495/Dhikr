package com.dhikr.app.core.backup

import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression coverage for fields that previously round-tripped through
 *  [BackupTasbih]/[BackupRoutine] as silent no-ops (backed up, but dropped
 *  on restore): per-item reminders, and a tasbih's cached AI benefits text. */
class BackupModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun tasbihReminderSurvivesBackupAndRestore() {
        val original = TasbihEntity(
            id = "custom-1",
            name = "Custom",
            arabic = "arabic",
            pronunciation = "pron",
            translation = "translation",
            lapTarget = 33,
            lapCount = 3,
            isBuiltIn = false,
            createdAt = 1L,
            updatedAt = 2L,
            reminderEnabled = true,
            reminderMinuteOfDay = 540,
            reminderDays = 0b0010101,
        )

        val backup = BackupTasbih.from(original)
        val restored = backup.toEntity()

        assertEquals(true, restored.reminderEnabled)
        assertEquals(540, restored.reminderMinuteOfDay)
        assertEquals(0b0010101, restored.reminderDays)
    }

    @Test
    fun cachedBenefitsTextSurvivesBackupAndRestore() {
        val original = TasbihEntity(
            id = "custom-2",
            name = "Custom",
            arabic = "arabic",
            pronunciation = "pron",
            translation = "translation",
            lapTarget = 33,
            lapCount = 3,
            isBuiltIn = false,
            createdAt = 1L,
            updatedAt = 2L,
            benefitsText = "Generated virtues text",
            benefitsGeneratedAt = 12345L,
        )

        val restored = BackupTasbih.from(original).toEntity()

        assertEquals("Generated virtues text", restored.benefitsText)
        assertEquals(12345L, restored.benefitsGeneratedAt)
    }

    @Test
    fun olderBackupFileWithoutTheseFieldsStillDecodes() {
        val decoded = json.decodeFromString(
            BackupTasbih.serializer(),
            """{"id":"x","name":"n","arabic":"a","translation":"t","lapTarget":33,"lapCount":3,"createdAt":1,"updatedAt":2}""",
        )
        val entity = decoded.toEntity()
        assertEquals(false, entity.reminderEnabled)
        assertEquals(null, entity.benefitsText)
    }

    @Test
    fun routineReminderRoundTripsThroughJson() {
        val routine = BackupRoutine(
            id = "r1",
            name = "Morning",
            createdAt = 1L,
            updatedAt = 2L,
            reminderEnabled = true,
            reminderMinuteOfDay = 300,
            reminderDays = 0b1111111,
        )
        val encoded = json.encodeToString(BackupRoutine.serializer(), routine)
        val decoded = json.decodeFromString(BackupRoutine.serializer(), encoded)
        val entity = decoded.toEntity()

        assertEquals(true, entity.reminderEnabled)
        assertEquals(300, entity.reminderMinuteOfDay)
        assertEquals(0b1111111, entity.reminderDays)
    }
}
