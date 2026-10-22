package com.dhikr.app.core.backup

import com.dhikr.app.core.database.entity.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SanitizeSessionRoutineIdTest {

    private fun session(routineId: String?) = SessionEntity(
        tasbihId = "tasbih-1",
        routineId = routineId,
        count = 33,
        startedAt = 0L,
        endedAt = 1000L,
    )

    @Test
    fun orphanedRoutineIdIsNulledOutInsteadOfTrippingTheForeignKey() {
        val result = sanitizeSessionRoutineId(session("deleted-routine"), validRoutineIds = emptySet())
        assertNull(
            "a routine the device no longer has must not be inserted as-is",
            result.routineId,
        )
    }

    @Test
    fun existingRoutineIdIsKeptUnchanged() {
        val result = sanitizeSessionRoutineId(session("morning"), validRoutineIds = setOf("morning", "evening"))
        assertEquals("morning", result.routineId)
    }

    @Test
    fun standaloneSessionWithNoRoutineIsUnaffected() {
        val result = sanitizeSessionRoutineId(session(null), validRoutineIds = setOf("morning"))
        assertNull(result.routineId)
    }
}
