package com.dhikr.app.core.prayer

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** The five daily prayers, in order. [bit] is its slot in the reminders mask. */
enum class Prayer(val bit: Int) { FAJR(0), DHUHR(1), ASR(2), MAGHRIB(3), ISHA(4) }

/**
 * Twilight conventions. [ishaMinutes] non-null means Isha is a fixed interval
 * after Maghrib rather than a twilight angle (Umm al-Qura).
 */
enum class CalculationMethod(val fajrAngle: Double, val ishaAngle: Double, val ishaMinutes: Int? = null) {
    KARACHI(18.0, 18.0),
    MWL(18.0, 17.0),
    ISNA(15.0, 15.0),
    EGYPT(19.5, 17.5),
    UMM_AL_QURA(18.5, 0.0, ishaMinutes = 90),
}

/** Asr shadow factor: STANDARD (Shafi'i, Maliki, Hanbali) = 1, HANAFI = 2. */
enum class AsrMethod(val shadowFactor: Int) { STANDARD(1), HANAFI(2) }

/**
 * Offline prayer-time calculation (the standard solar-position method used by
 * praytimes.org and most prayer apps). Pure — no Android, no network, no
 * location services: the caller supplies coordinates. Accuracy is a minute or
 * two, which is plenty for "remind me after the prayer".
 *
 * Calendar rather than java.time for the same minSdk-24 reason as
 * NextReminderTime.
 */
object PrayerTimes {

    /**
     * Prayer instants (epoch millis) for the local calendar day containing
     * [dayMillis] in [zone]. A prayer whose sun angle is never reached that
     * day (far-north summer twilight) is absent from the map rather than
     * guessed.
     */
    fun forDay(
        dayMillis: Long,
        latitude: Double,
        longitude: Double,
        method: CalculationMethod,
        asr: AsrMethod,
        zone: TimeZone = TimeZone.getDefault(),
    ): Map<Prayer, Long> {
        val midnight = Calendar.getInstance(zone).apply {
            timeInMillis = dayMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val year = midnight.get(Calendar.YEAR)
        val month = midnight.get(Calendar.MONTH) + 1
        val day = midnight.get(Calendar.DAY_OF_MONTH)
        // Offset at local noon, so a DST switch at 02:00 doesn't skew the day.
        val noonMillis = midnight.timeInMillis + 12 * HOUR_MILLIS
        val tzHours = zone.getOffset(noonMillis) / HOUR_MILLIS.toDouble()
        // Julian date at 00:00 UT of this day, shifted by longitude so the sun
        // position is evaluated near local solar time.
        val jDate = julian(year, month, day) - longitude / (15.0 * 24.0)

        fun sun(hours: Double) = sunPosition(jDate + hours / 24.0)

        // Solar noon, in local clock hours.
        fun midDay(hours: Double): Double = fixHour(12 - sun(hours).equation)

        // Hours from noon until the sun is [angle] degrees below the horizon,
        // or null if it never gets there.
        fun timeForAngle(angle: Double, hours: Double, ccw: Boolean): Double? {
            val decl = sun(hours).declination
            val noon = midDay(hours)
            val cosT = (-sinD(angle) - sinD(decl) * sinD(latitude)) / (cosD(decl) * cosD(latitude))
            if (cosT < -1 || cosT > 1) return null
            val t = arccosD(cosT) / 15.0
            return noon + if (ccw) -t else t
        }

        fun asrTime(hours: Double): Double? {
            val decl = sun(hours).declination
            val angle = -arccotD(asr.shadowFactor + tanD(abs(latitude - decl)))
            return timeForAngle(angle, hours, ccw = false)
        }

        // Initial guesses (hours of the day), refined once with the sun's
        // position at that guess — the standard single-iteration approach.
        val fajr = timeForAngle(method.fajrAngle, 5.0, ccw = true)
        val dhuhr = midDay(12.0)
        val asrT = asrTime(13.0)
        val maghrib = timeForAngle(SUNSET_ANGLE, 18.0, ccw = false)
        val isha = if (method.ishaMinutes != null) {
            maghrib?.plus(method.ishaMinutes / 60.0)
        } else {
            timeForAngle(method.ishaAngle, 18.0, ccw = false)
        }

        // Convert solar-local hours to the zone's clock and then to millis.
        val adjust = tzHours - longitude / 15.0
        fun toMillis(h: Double?): Long? =
            h?.let { midnight.timeInMillis + ((it + adjust) * HOUR_MILLIS).toLong() }

        return buildMap {
            toMillis(fajr)?.let { put(Prayer.FAJR, it) }
            // Dhuhr a minute after the zenith, by convention.
            toMillis(dhuhr + 1 / 60.0)?.let { put(Prayer.DHUHR, it) }
            toMillis(asrT)?.let { put(Prayer.ASR, it) }
            toMillis(maghrib)?.let { put(Prayer.MAGHRIB, it) }
            toMillis(isha)?.let { put(Prayer.ISHA, it) }
        }
    }

    /**
     * First instant strictly after [nowMillis] that is [offsetMinutes] past
     * [prayer], looking at today then the next few days (a prayer can be
     * missing on a polar day). Null if none is found within a week.
     */
    fun nextOccurrence(
        prayer: Prayer,
        nowMillis: Long,
        offsetMinutes: Int,
        latitude: Double,
        longitude: Double,
        method: CalculationMethod,
        asr: AsrMethod,
        zone: TimeZone = TimeZone.getDefault(),
    ): Long? {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        repeat(8) {
            val at = forDay(cal.timeInMillis, latitude, longitude, method, asr, zone)[prayer]
                ?.plus(offsetMinutes * MINUTE_MILLIS)
            if (at != null && at > nowMillis) return at
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }

    private data class SunPosition(val declination: Double, val equation: Double)

    private fun sunPosition(jd: Double): SunPosition {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinD(g) + 0.020 * sinD(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = arctan2D(cosD(e) * sinD(l), cosD(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = arcsinD(sinD(e) * sinD(l))
        return SunPosition(declination = decl, equation = eqt)
    }

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private const val SUNSET_ANGLE = 0.833
    private const val HOUR_MILLIS = 3_600_000L
    private const val MINUTE_MILLIS = 60_000L

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun sinD(d: Double) = sin(rad(d))
    private fun cosD(d: Double) = cos(rad(d))
    private fun tanD(d: Double) = tan(rad(d))
    private fun arcsinD(x: Double) = deg(asin(x))
    private fun arccosD(x: Double) = deg(acos(x))
    private fun arctan2D(y: Double, x: Double) = deg(atan2(y, x))
    private fun arccotD(x: Double) = deg(atan(1 / x))
    private fun fixAngle(a: Double) = a - 360.0 * floor(a / 360.0)
    private fun fixHour(h: Double) = h - 24.0 * floor(h / 24.0)
}
