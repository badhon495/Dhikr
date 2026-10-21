package com.dhikr.app.core.utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class IslamicSeasonsTest {

    @Test
    fun ramadanBeatsFriday() {
        assertEquals(IslamicSeason.RAMADAN, IslamicSeasons.detect(HijriDate(5, 9, 1447), Calendar.FRIDAY))
    }

    @Test
    fun dhulHijjahCoversTashreeqOnly() {
        assertEquals(IslamicSeason.DHUL_HIJJAH, IslamicSeasons.detect(HijriDate(13, 12, 1447), Calendar.MONDAY))
        assertNull(IslamicSeasons.detect(HijriDate(14, 12, 1447), Calendar.MONDAY))
    }

    @Test
    fun ordinaryFriday() {
        assertEquals(IslamicSeason.FRIDAY, IslamicSeasons.detect(HijriDate(3, 4, 1447), Calendar.FRIDAY))
        assertNull(IslamicSeasons.detect(HijriDate(3, 4, 1447), Calendar.THURSDAY))
    }
}
