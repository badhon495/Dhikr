package com.dhikr.app.core.prayer

/**
 * Everything needed to compute and schedule prayer-linked reminders. No
 * location is stored until the user picks a city or enters coordinates —
 * [hasLocation] gates the whole feature.
 */
data class PrayerSettings(
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** City name, or null for hand-entered coordinates. */
    val locationName: String? = null,
    val method: CalculationMethod = CalculationMethod.KARACHI,
    val asr: AsrMethod = AsrMethod.HANAFI,
    /** Bit [Prayer.bit] set = remind after that prayer. */
    val remindersMask: Int = 0,
    /** Minutes after the prayer time to remind. */
    val offsetMinutes: Int = DEFAULT_PRAYER_OFFSET_MINUTES,
    /** Routine each prayer's reminder opens; absent = [DEFAULT_PRAYER_ROUTINE_ID]. */
    val routineIds: Map<Prayer, String> = emptyMap(),
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null

    fun routineFor(prayer: Prayer): String = routineIds[prayer] ?: DEFAULT_PRAYER_ROUTINE_ID

    fun isEnabled(prayer: Prayer): Boolean = remindersMask and (1 shl prayer.bit) != 0
}

const val DEFAULT_PRAYER_OFFSET_MINUTES = 15

/** The preset "After Salah" routine seeded in SeedData. */
const val DEFAULT_PRAYER_ROUTINE_ID = "after_salah"
val PRAYER_OFFSET_OPTIONS = listOf(5, 15, 30, 45)
