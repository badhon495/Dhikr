package com.dhikr.app.core.sync

import com.dhikr.app.core.backup.BackupFile
import com.dhikr.app.core.backup.BackupPreferences
import com.dhikr.app.core.backup.BackupRoutine
import com.dhikr.app.core.backup.BackupRoutineStep
import com.dhikr.app.core.backup.BackupSession
import com.dhikr.app.core.backup.BackupTasbih
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private fun t(id: String, name: String = id, updatedAt: Long = 1L) = BackupTasbih(
        id = id, name = name, arabic = "", translation = "",
        lapTarget = 33, lapCount = 3, createdAt = 0L, updatedAt = updatedAt,
    )

    private fun r(id: String, vararg tasbihIds: String) = BackupRoutine(
        id = id, name = id, createdAt = 0L, updatedAt = 1L,
        steps = tasbihIds.mapIndexed { i, tid -> BackupRoutineStep(tid, i, 10) },
    )

    private fun s(tasbihId: String, startedAt: Long, count: Int = 10) =
        BackupSession(tasbihId = tasbihId, count = count, startedAt = startedAt, endedAt = startedAt + 5)

    private fun file(
        tasbih: List<BackupTasbih> = emptyList(),
        routines: List<BackupRoutine> = emptyList(),
        favs: List<String> = emptyList(),
        sessions: List<BackupSession> = emptyList(),
        goal: Int? = null,
    ) = BackupFile(
        customTasbih = tasbih, customRoutines = routines, builtInFavorites = favs,
        sessions = sessions, preferences = BackupPreferences(dailyGoalTarget = goal),
    )

    @Test
    fun firstSyncEmptyRemoteUploadsEverythingAndDeletesNothing() {
        val local = file(listOf(t("a")), listOf(r("r1")), listOf("fav"), listOf(s("a", 1)), goal = 700)
        val res = mergeState(local, remote = null, base = null)
        assertEquals(listOf("a"), res.merged.customTasbih.map { it.id })
        assertEquals(listOf("r1"), res.merged.customRoutines.map { it.id })
        assertEquals(listOf("fav"), res.merged.builtInFavorites)
        assertEquals(1, res.merged.sessions.size)
        assertEquals(700, res.merged.preferences.dailyGoalTarget)
        assertEquals(0, res.localChanges)
        assertTrue(res.remoteChanges > 0)
    }

    @Test
    fun remoteOnlyEditIsPulled() {
        val base = file(listOf(t("a", "old", 1)))
        val remote = file(listOf(t("a", "new", 5)))
        val res = mergeState(base, remote, base)
        assertEquals("new", res.merged.customTasbih.single().name)
        assertEquals(1, res.localChanges)
        assertEquals(0, res.remoteChanges)
    }

    @Test
    fun bothEditedHigherUpdatedAtWins() {
        val base = file(listOf(t("a", "old", 1)))
        val local = file(listOf(t("a", "local", 9)))
        val remote = file(listOf(t("a", "remote", 5)))
        assertEquals("local", mergeState(local, remote, base).merged.customTasbih.single().name)
    }

    @Test
    fun bothEditedTieRemoteWins() {
        val base = file(listOf(t("a", "old", 1)))
        val local = file(listOf(t("a", "local", 5)))
        val remote = file(listOf(t("a", "remote", 5)))
        assertEquals("remote", mergeState(local, remote, base).merged.customTasbih.single().name)
    }

    @Test
    fun deletedLocallyIsDeletedRemotely() {
        val base = file(listOf(t("a"), t("b")))
        val local = file(listOf(t("b")))
        val res = mergeState(local, base, base)
        assertEquals(listOf("b"), res.merged.customTasbih.map { it.id })
        assertEquals(1, res.remoteChanges)
    }

    @Test
    fun deletedRemotelyIsDeletedLocally() {
        val base = file(listOf(t("a"), t("b")))
        val remote = file(listOf(t("b")))
        val res = mergeState(base, remote, base)
        assertEquals(listOf("b"), res.merged.customTasbih.map { it.id })
        assertEquals(1, res.localChanges)
    }

    @Test
    fun editBeatsDelete() {
        val base = file(listOf(t("a", "old", 1)))
        val edited = file(listOf(t("a", "edited", 7)))
        assertEquals("edited", mergeState(edited, file(), base).merged.customTasbih.single().name)
        assertEquals("edited", mergeState(file(), edited, base).merged.customTasbih.single().name)
    }

    @Test
    fun tasbihKeptWhileSurvivingRoutineUsesIt() {
        val base = file(listOf(t("a")))
        val local = file() // local deleted tasbih a
        val remote = file(listOf(t("a")), listOf(r("r1", "a"))) // remote added routine using it
        val res = mergeState(local, remote, base)
        assertEquals(listOf("a"), res.merged.customTasbih.map { it.id })
        assertEquals(listOf("r1"), res.merged.customRoutines.map { it.id })
    }

    @Test
    fun noBaseNeverDeletes() {
        val res = mergeState(file(listOf(t("a"))), file(listOf(t("b"))), base = null)
        assertEquals(listOf("a", "b"), res.merged.customTasbih.map { it.id })
    }

    @Test
    fun sessionsUnionByKeyNoDuplicatesOnRepeat() {
        val local = file(listOf(t("a")), sessions = listOf(s("a", 1), s("a", 2)))
        val remote = file(listOf(t("a")), sessions = listOf(s("a", 2), s("a", 3)))
        val once = mergeState(local, remote, null).merged
        assertEquals(3, once.sessions.size)
        assertEquals(3, mergeState(once, once, once).merged.sessions.size)
    }

    @Test
    fun sessionsOfDeletedTasbihDropped() {
        val base = file(listOf(t("a")), sessions = listOf(s("a", 1)))
        val local = file() // delete cascaded the sessions away
        val res = mergeState(local, base, base)
        assertTrue(res.merged.customTasbih.isEmpty())
        assertTrue(res.merged.sessions.isEmpty())
    }

    @Test
    fun sessionsForUnknownTasbihKeptInMerged() {
        val local = file(sessions = listOf(s("builtin_x", 1)))
        val remote = file(sessions = listOf(s("retired_id", 2)))
        assertEquals(2, mergeState(local, remote, null).merged.sessions.size)
    }

    @Test
    fun favouritesThreeWay() {
        val base = file(favs = listOf("x", "y"))
        val local = file(favs = listOf("x")) // removed y
        val remote = file(favs = listOf("x", "y", "z")) // added z
        assertEquals(listOf("x", "z"), mergeState(local, remote, base).merged.builtInFavorites.sorted())
    }

    @Test
    fun goalLocalChangedWins() {
        val base = file(goal = 500)
        val remote = file(goal = 600)
        assertEquals(800, mergeState(file(goal = 800), remote, base).merged.preferences.dailyGoalTarget)
        assertEquals(600, mergeState(base, remote, base).merged.preferences.dailyGoalTarget)
    }

    @Test
    fun goalFirstSyncRemoteWins() {
        assertEquals(900, mergeState(file(goal = 500), file(goal = 900), null).merged.preferences.dailyGoalTarget)
    }

    @Test
    fun diffSessionsSkipsInvalidTasbihAndDeletesExtras() {
        val keep = s("a", 1)
        val add = s("a", 2)
        val diff = diffSessions(
            localKeys = setOf(sessionKey(keep), "a|99|104|10"),
            target = listOf(keep, add, s("zzz", 3)),
            validTasbihIds = setOf("a"),
        )
        assertEquals(listOf(add), diff.toInsert)
        assertEquals(setOf("a|99|104|10"), diff.toDeleteKeys)
    }
}
