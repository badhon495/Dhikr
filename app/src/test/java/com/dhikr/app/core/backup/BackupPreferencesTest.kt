package com.dhikr.app.core.backup

import com.dhikr.app.core.prayer.AsrMethod
import com.dhikr.app.core.prayer.CalculationMethod
import com.dhikr.app.core.prayer.Prayer
import com.dhikr.app.core.prayer.PrayerSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupPreferencesTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun olderBackupWithoutNewFieldsStillDecodes() {
        val prefs = json.decodeFromString(BackupPreferences.serializer(), """{"dailyGoalTarget":700}""")
        assertEquals(700, prefs.dailyGoalTarget)
        assertNull(prefs.weeklyGoalTarget)
        assertNull(prefs.prayer)
    }

    @Test
    fun prayerSettingsRoundTrip() {
        val settings = PrayerSettings(
            latitude = 23.81, longitude = 90.41, locationName = "Dhaka",
            method = CalculationMethod.MWL, asr = AsrMethod.STANDARD,
            remindersMask = 0b10101, offsetMinutes = 30,
            routineIds = mapOf(Prayer.FAJR to "morning", Prayer.ASR to "evening"),
        )
        val encoded = json.encodeToString(BackupPreferences.serializer(), BackupPreferences(prayer = BackupPrayerSettings.from(settings)))
        val decoded = json.decodeFromString(BackupPreferences.serializer(), encoded)
        assertEquals(settings, decoded.prayer?.toPrayerSettings())
    }

    @Test
    fun noLocationMeansNothingToBackUp() {
        assertNull(BackupPrayerSettings.from(PrayerSettings()))
    }

    @Test
    fun unknownMethodFallsBackToDefault() {
        val restored = BackupPrayerSettings(latitude = 1.0, longitude = 2.0, method = "NOPE").toPrayerSettings()
        assertEquals(PrayerSettings().method, restored?.method)
    }
}
