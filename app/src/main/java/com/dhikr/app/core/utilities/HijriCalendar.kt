package com.dhikr.app.core.utilities

import android.icu.util.IslamicCalendar
import java.util.concurrent.TimeUnit

/**
 * Today's Hijri date from the platform's ICU Umm al-Qura calendar (API 24+, so
 * no library and no network). Local moon sighting often differs by a day, so
 * the user-set [adjustDays] (-2..+2) shifts the result.
 */
object HijriCalendar {

    fun of(millis: Long, adjustDays: Int = 0): HijriDate {
        val cal = IslamicCalendar().apply {
            calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
            timeInMillis = millis + TimeUnit.DAYS.toMillis(adjustDays.toLong())
        }
        return HijriDate(
            day = cal.get(android.icu.util.Calendar.DAY_OF_MONTH),
            month = cal.get(android.icu.util.Calendar.MONTH) + 1,
            year = cal.get(android.icu.util.Calendar.YEAR),
        )
    }
}
