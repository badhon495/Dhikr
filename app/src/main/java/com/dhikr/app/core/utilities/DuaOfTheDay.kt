package com.dhikr.app.core.utilities

import com.dhikr.app.core.database.entity.DuaEntity
import java.util.TimeZone

/**
 * One dua a day for Home. Deterministic — the same dua all day, stepping
 * through the library in a stable (category, order) sequence so every dua gets
 * its turn before any repeats.
 */
object DuaOfTheDay {

    private const val DAY_MILLIS = 86_400_000L

    /** Days since 1970-01-01 in the device's local time zone. */
    fun localEpochDay(nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        Math.floorDiv(nowMillis + timeZone.getOffset(nowMillis), DAY_MILLIS)

    fun pick(duas: List<DuaEntity>, epochDay: Long): DuaEntity? {
        if (duas.isEmpty()) return null
        val ordered = duas.sortedWith(compareBy({ it.categoryOrder }, { it.order }, { it.id }))
        return ordered[Math.floorMod(epochDay, ordered.size.toLong()).toInt()]
    }
}
