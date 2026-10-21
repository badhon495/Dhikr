package com.dhikr.app.core.utilities

import java.util.Calendar

/**
 * Special times shown as a gentle Home banner. Ordered by precedence: a
 * Friday in Ramadan shows Ramadan.
 */
enum class IslamicSeason { RAMADAN, DHUL_HIJJAH, FRIDAY }

/** A Hijri date. [month] is 1-based (1 = Muharram .. 12 = Dhul Hijjah). */
data class HijriDate(val day: Int, val month: Int, val year: Int)

object IslamicSeasons {

    /**
     * @param dayOfWeek a [Calendar.DAY_OF_WEEK] value for the same day.
     */
    fun detect(hijri: HijriDate, dayOfWeek: Int): IslamicSeason? = when {
        hijri.month == 9 -> IslamicSeason.RAMADAN
        // The first ten days plus the days of Tashreeq (11-13).
        hijri.month == 12 && hijri.day <= 13 -> IslamicSeason.DHUL_HIJJAH
        dayOfWeek == Calendar.FRIDAY -> IslamicSeason.FRIDAY
        else -> null
    }
}
