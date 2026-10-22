package com.dhikr.app.core.utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class IslamicSeasonsTest {

    private val plainTomorrow = HijriDate(4, 4, 1447) to Calendar.TUESDAY

    private fun detect(today: HijriDate, dow: Int, tomorrow: Pair<HijriDate, Int> = plainTomorrow) =
        IslamicSeasons.detect(today, dow, tomorrow.first, tomorrow.second)

    @Test
    fun ramadanBeatsFriday() {
        assertEquals(IslamicSeason.RAMADAN, detect(HijriDate(5, 9, 1447), Calendar.FRIDAY))
    }

    @Test
    fun lastTenNightsOfRamadan() {
        assertEquals(IslamicSeason.LAST_TEN_NIGHTS, detect(HijriDate(21, 9, 1447), Calendar.MONDAY))
        assertEquals(IslamicSeason.RAMADAN, detect(HijriDate(20, 9, 1447), Calendar.MONDAY))
    }

    @Test
    fun arafahBeatsDhulHijjah() {
        assertEquals(IslamicSeason.ARAFAH, detect(HijriDate(9, 12, 1447), Calendar.MONDAY))
        assertEquals(IslamicSeason.DHUL_HIJJAH, detect(HijriDate(13, 12, 1447), Calendar.MONDAY))
    }

    @Test
    fun ashuraIsNinthAndTenthOfMuharram() {
        assertEquals(IslamicSeason.ASHURA, detect(HijriDate(10, 1, 1448), Calendar.MONDAY))
        assertEquals(IslamicSeason.ASHURA, detect(HijriDate(9, 1, 1448), Calendar.MONDAY))
    }

    @Test
    fun ordinaryFriday() {
        assertEquals(IslamicSeason.FRIDAY, detect(HijriDate(3, 4, 1447), Calendar.FRIDAY))
    }

    @Test
    fun whiteDays() {
        assertEquals(IslamicSeason.WHITE_DAYS, detect(HijriDate(14, 4, 1447), Calendar.TUESDAY))
    }

    @Test
    fun eveOfMondayFast() {
        assertEquals(
            IslamicSeason.FAST_TOMORROW,
            detect(HijriDate(3, 4, 1447), Calendar.SUNDAY, HijriDate(4, 4, 1447) to Calendar.MONDAY),
        )
    }

    @Test
    fun nothingSpecial() {
        assertNull(detect(HijriDate(3, 4, 1447), Calendar.TUESDAY))
    }

    @Test
    fun sunnahFastDays() {
        assertTrue(IslamicSeasons.isSunnahFast(HijriDate(4, 4, 1447), Calendar.THURSDAY))
        assertTrue(IslamicSeasons.isSunnahFast(HijriDate(13, 4, 1447), Calendar.TUESDAY))
        assertTrue(IslamicSeasons.isSunnahFast(HijriDate(9, 12, 1447), Calendar.TUESDAY))
        assertTrue(IslamicSeasons.isSunnahFast(HijriDate(10, 1, 1448), Calendar.TUESDAY))
    }

    @Test
    fun neverSuggestsFastingOnForbiddenOrObligatoryDays() {
        // Eid al-Fitr and Eid al-Adha / Tashreeq, even on a Monday or white day.
        assertFalse(IslamicSeasons.isSunnahFast(HijriDate(1, 10, 1447), Calendar.MONDAY))
        assertFalse(IslamicSeasons.isSunnahFast(HijriDate(10, 12, 1447), Calendar.THURSDAY))
        assertFalse(IslamicSeasons.isSunnahFast(HijriDate(13, 12, 1447), Calendar.MONDAY))
        // Ramadan is already obligatory.
        assertFalse(IslamicSeasons.isSunnahFast(HijriDate(14, 9, 1447), Calendar.MONDAY))
    }

    @Test
    fun fastReasonPrefersTheSpecificDay() {
        // Arafah on a Monday is "Arafah", a white day on a Thursday is "white day".
        assertEquals(FastReason.ARAFAH, IslamicSeasons.fastReason(HijriDate(9, 12, 1447), Calendar.MONDAY))
        assertEquals(FastReason.WHITE_DAYS, IslamicSeasons.fastReason(HijriDate(14, 3, 1447), Calendar.THURSDAY))
        assertEquals(FastReason.MONDAY, IslamicSeasons.fastReason(HijriDate(4, 3, 1447), Calendar.MONDAY))
        assertNull(IslamicSeasons.fastReason(HijriDate(4, 3, 1447), Calendar.FRIDAY))
    }
}
