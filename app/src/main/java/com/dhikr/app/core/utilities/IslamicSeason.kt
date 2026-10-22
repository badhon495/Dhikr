package com.dhikr.app.core.utilities

import java.util.Calendar

/**
 * Special times shown as a gentle Home banner, in precedence order: the first
 * that applies wins (a Friday in Ramadan shows Ramadan).
 */
enum class IslamicSeason {
    LAST_TEN_NIGHTS,
    RAMADAN,
    ARAFAH,
    DHUL_HIJJAH,
    ASHURA,
    FRIDAY,
    WHITE_DAYS,
    /** Tomorrow is a Sunnah fast (Monday/Thursday, white day, Arafah, Ashura). */
    FAST_TOMORROW,
}

/** Why a day is a recommended voluntary fast. */
enum class FastReason { ARAFAH, ASHURA, WHITE_DAYS, MONDAY, THURSDAY }

/** A Hijri date. [month] is 1-based (1 = Muharram .. 12 = Dhul Hijjah). */
data class HijriDate(val day: Int, val month: Int, val year: Int)

object IslamicSeasons {

    private const val MUHARRAM = 1
    private const val RAMADAN = 9
    private const val SHAWWAL = 10
    private const val DHUL_HIJJAH = 12

    /**
     * @param dayOfWeek / [tomorrowDayOfWeek] are [Calendar.DAY_OF_WEEK] values.
     */
    fun detect(
        today: HijriDate,
        dayOfWeek: Int,
        tomorrow: HijriDate,
        tomorrowDayOfWeek: Int,
    ): IslamicSeason? = when {
        today.month == RAMADAN && today.day >= 21 -> IslamicSeason.LAST_TEN_NIGHTS
        today.month == RAMADAN -> IslamicSeason.RAMADAN
        today.month == DHUL_HIJJAH && today.day == 9 -> IslamicSeason.ARAFAH
        // The first ten days plus the days of Tashreeq (11-13).
        today.month == DHUL_HIJJAH && today.day <= 13 -> IslamicSeason.DHUL_HIJJAH
        today.month == MUHARRAM && today.day in 9..10 -> IslamicSeason.ASHURA
        dayOfWeek == Calendar.FRIDAY -> IslamicSeason.FRIDAY
        today.day in 13..15 && isSunnahFast(today, dayOfWeek) -> IslamicSeason.WHITE_DAYS
        isSunnahFast(tomorrow, tomorrowDayOfWeek) -> IslamicSeason.FAST_TOMORROW
        else -> null
    }

    /**
     * Whether [date] is a recommended voluntary fast: Monday or Thursday, the
     * white days (13-15), the day of Arafah, or Ashura (9th/10th Muharram).
     * Never true in Ramadan (already obligatory) or on the days fasting is
     * forbidden — Eid al-Fitr and Eid al-Adha with the days of Tashreeq.
     */
    fun isSunnahFast(date: HijriDate, dayOfWeek: Int): Boolean = fastReason(date, dayOfWeek) != null

    /** Why [date] is a Sunnah fast (most specific reason first), or null. */
    fun fastReason(date: HijriDate, dayOfWeek: Int): FastReason? = when {
        date.month == RAMADAN -> null
        date.month == SHAWWAL && date.day == 1 -> null
        date.month == DHUL_HIJJAH && date.day in 10..13 -> null
        date.month == DHUL_HIJJAH && date.day == 9 -> FastReason.ARAFAH
        date.month == MUHARRAM && date.day in 9..10 -> FastReason.ASHURA
        date.day in 13..15 -> FastReason.WHITE_DAYS
        dayOfWeek == Calendar.MONDAY -> FastReason.MONDAY
        dayOfWeek == Calendar.THURSDAY -> FastReason.THURSDAY
        else -> null
    }
}
