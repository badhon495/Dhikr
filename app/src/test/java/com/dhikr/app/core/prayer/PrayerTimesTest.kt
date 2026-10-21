package com.dhikr.app.core.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PrayerTimesTest {

    private val dhaka = TimeZone.getTimeZone("Asia/Dhaka")

    private fun dhakaDay(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(dhaka).apply { clear(); set(year, month - 1, day, 12, 0) }.timeInMillis

    private fun minuteOfDay(millis: Long, zone: TimeZone): Int =
        Calendar.getInstance(zone).run {
            timeInMillis = millis
            get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
        }

    private fun assertNear(expected: Int, actual: Int, tolerance: Int = 4) =
        assertTrue("expected ~$expected, was $actual", kotlin.math.abs(expected - actual) <= tolerance)

    @Test
    fun dhakaNewYear_matchesHandCheckedTimes() {
        // Dhaka, 1 Jan 2024, Karachi angles, Hanafi Asr. Sunset matches the
        // well-known ~17:25; Asr worked by hand: declination -23.0, shadow
        // factor 2 -> sun altitude 18.07 deg -> 3.75 h after the 12:01 zenith.
        val times = PrayerTimes.forDay(
            dhakaDay(2024, 1, 1), 23.8103, 90.4125,
            CalculationMethod.KARACHI, AsrMethod.HANAFI, dhaka,
        )
        assertNear(5 * 60 + 18, minuteOfDay(times.getValue(Prayer.FAJR), dhaka))
        assertNear(12 * 60 + 2, minuteOfDay(times.getValue(Prayer.DHUHR), dhaka))
        assertNear(15 * 60 + 46, minuteOfDay(times.getValue(Prayer.ASR), dhaka))
        assertNear(17 * 60 + 25, minuteOfDay(times.getValue(Prayer.MAGHRIB), dhaka))
        assertNear(18 * 60 + 43, minuteOfDay(times.getValue(Prayer.ISHA), dhaka))
    }

    @Test
    fun prayersAreInOrder_andHanafiAsrIsLater() {
        val day = dhakaDay(2024, 6, 15)
        val standard = PrayerTimes.forDay(day, 23.8103, 90.4125, CalculationMethod.MWL, AsrMethod.STANDARD, dhaka)
        val hanafi = PrayerTimes.forDay(day, 23.8103, 90.4125, CalculationMethod.MWL, AsrMethod.HANAFI, dhaka)
        val ordered = Prayer.entries.map { standard.getValue(it) }
        assertEquals(ordered.sorted(), ordered)
        assertTrue(hanafi.getValue(Prayer.ASR) > standard.getValue(Prayer.ASR))
    }

    @Test
    fun ummAlQura_ishaIsNinetyMinutesAfterMaghrib() {
        val times = PrayerTimes.forDay(
            dhakaDay(2024, 3, 1), 21.3891, 39.8579,
            CalculationMethod.UMM_AL_QURA, AsrMethod.STANDARD, dhaka,
        )
        assertEquals(90L * 60_000, times.getValue(Prayer.ISHA) - times.getValue(Prayer.MAGHRIB))
    }

    @Test
    fun polarSummer_omitsUnreachableTwilight() {
        val oslo = TimeZone.getTimeZone("Europe/Oslo")
        val midsummer = Calendar.getInstance(oslo).apply { clear(); set(2024, 5, 21, 12, 0) }.timeInMillis
        val times = PrayerTimes.forDay(midsummer, 69.6492, 18.9553, CalculationMethod.MWL, AsrMethod.STANDARD, oslo)
        assertNull(times[Prayer.FAJR])
        assertNotNull(times[Prayer.DHUHR])
    }

    @Test
    fun nextOccurrence_rollsToTomorrowOncePassed() {
        val evening = Calendar.getInstance(dhaka).apply { clear(); set(2024, 0, 1, 21, 0) }.timeInMillis
        val next = PrayerTimes.nextOccurrence(
            Prayer.FAJR, evening, 10, 23.8103, 90.4125,
            CalculationMethod.KARACHI, AsrMethod.HANAFI, dhaka,
        )!!
        assertTrue(next > evening)
        val cal = Calendar.getInstance(dhaka).apply { timeInMillis = next }
        assertEquals(2, cal.get(Calendar.DAY_OF_MONTH))
        assertNear(5 * 60 + 28, minuteOfDay(next, dhaka))
    }
}
