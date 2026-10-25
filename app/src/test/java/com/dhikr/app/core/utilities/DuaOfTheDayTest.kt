package com.dhikr.app.core.utilities

import com.dhikr.app.core.database.entity.DuaEntity
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuaOfTheDayTest {

    private fun dua(id: String, categoryOrder: Int, order: Int) = DuaEntity(
        id = id, category = "c", categoryOrder = categoryOrder, order = order,
        name = id, arabic = "a", translation = "t",
    )

    private val duas = listOf(dua("c", 2, 0), dua("a", 1, 0), dua("b", 1, 1))

    @Test
    fun emptyLibrary_returnsNull() {
        assertNull(DuaOfTheDay.pick(emptyList(), 5))
    }

    @Test
    fun stepsThroughLibraryInStableOrder() {
        assertEquals("a", DuaOfTheDay.pick(duas, 0)?.id)
        assertEquals("b", DuaOfTheDay.pick(duas, 1)?.id)
        assertEquals("c", DuaOfTheDay.pick(duas, 2)?.id)
        assertEquals("a", DuaOfTheDay.pick(duas, 3)?.id)
    }

    @Test
    fun sameDayGivesSameDua_regardlessOfInputOrder() {
        assertEquals(DuaOfTheDay.pick(duas, 20_000)?.id, DuaOfTheDay.pick(duas.reversed(), 20_000)?.id)
    }

    @Test
    fun localEpochDay_followsTimeZoneMidnight() {
        val utcPlus6 = TimeZone.getTimeZone("GMT+6")
        val dayMillis = 86_400_000L
        // 20:00 UTC on epoch day 10 is already 02:00 on day 11 at UTC+6.
        val now = 10 * dayMillis + 20 * 3_600_000L
        assertEquals(11L, DuaOfTheDay.localEpochDay(now, utcPlus6))
        assertEquals(10L, DuaOfTheDay.localEpochDay(now, TimeZone.getTimeZone("UTC")))
    }
}
