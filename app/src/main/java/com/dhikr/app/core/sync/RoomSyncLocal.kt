package com.dhikr.app.core.sync

import androidx.room.withTransaction
import com.dhikr.app.core.backup.BackupFile
import com.dhikr.app.core.backup.BackupRepository
import com.dhikr.app.core.backup.BackupRoutineStep
import com.dhikr.app.core.backup.BackupSession
import com.dhikr.app.core.backup.BackupTasbih
import com.dhikr.app.core.database.AppDatabase
import com.dhikr.app.core.datastore.AppPreferencesRepository

/** This device's side of a sync: read its state, and make it equal a target state. */
interface SyncLocal {
    suspend fun readState(): BackupFile

    /** Makes custom tasbih, custom routines, built-in favourites, sessions and the
     *  daily goal equal [target]. */
    suspend fun applyState(target: BackupFile)
}

class RoomSyncLocal(
    private val database: AppDatabase,
    private val backup: BackupRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val appVersionName: String,
) : SyncLocal {

    override suspend fun readState(): BackupFile = backup.buildFile(appVersionName)

    override suspend fun applyState(target: BackupFile) {
        val tasbihDao = database.tasbihDao()
        val routineDao = database.routineDao()
        val sessionDao = database.sessionDao()

        database.withTransaction {
            val localRoutines = routineDao.getAllCustomWithSteps().associateBy { it.routine.id }
            val localTasbih = tasbihDao.getAllCustom().associateBy { it.id }
            val targetRoutineIds = target.customRoutines.mapTo(HashSet()) { it.id }
            val targetTasbihIds = target.customTasbih.mapTo(HashSet()) { it.id }

            // 1. Routines gone from the target (cascades their steps, freeing tasbih).
            localRoutines.values
                .filter { it.routine.id !in targetRoutineIds }
                .forEach { routineDao.deleteRoutine(it.routine) }

            // 2. New/changed tasbih. Copy synced fields onto the existing row so
            //    cached benefits text and reminders on this device survive.
            //    Never REPLACE an existing row: SQLite implements that as delete+insert, which
            //    fires the foreign keys (cascades sessions/progress/steps, or RESTRICT-fails
            //    when a routine uses the tasbih). New rows are inserted, existing ones updated.
            target.customTasbih.forEach { b ->
                val existing = localTasbih[b.id]
                when {
                    existing == null -> tasbihDao.insert(b.toEntity())
                    BackupTasbih.from(existing) == b -> Unit
                    else -> tasbihDao.update(
                        existing.copy(
                            name = b.name, arabic = b.arabic, pronunciation = b.pronunciation,
                            translation = b.translation, note = b.note, source = b.source,
                            lapTarget = b.lapTarget, lapCount = b.lapCount, dailyGoal = b.dailyGoal,
                            isFavorite = b.isFavorite, updatedAt = b.updatedAt,
                        ),
                    )
                }
            }

            // 3. New/changed routines with their steps.
            val validTasbihIds = tasbihDao.getAllIds().toHashSet()
            target.customRoutines.forEach { b ->
                // A step naming a tasbih this device doesn't have (e.g. a built-in from a newer
                // app version) would violate the foreign key; leave that routine as it is here.
                if (b.steps.any { it.tasbihId !in validTasbihIds }) return@forEach
                val existing = localRoutines[b.id]
                val sameSteps = existing?.steps?.sortedBy { it.stepOrder }
                    ?.map(BackupRoutineStep::from) == b.steps
                val unchanged = existing != null && sameSteps &&
                    existing.routine.name == b.name &&
                    existing.routine.isFavorite == b.isFavorite &&
                    existing.routine.updatedAt == b.updatedAt
                if (!unchanged) {
                    if (existing == null) {
                        routineDao.upsertRoutine(b.toEntity())
                    } else {
                        routineDao.updateRoutine(
                            existing.routine.copy(
                                name = b.name, isFavorite = b.isFavorite, updatedAt = b.updatedAt,
                            ),
                        )
                    }
                    if (!sameSteps) routineDao.replaceSteps(b.id, b.steps.map { it.toEntity(b.id) })
                }
            }

            // 4. Tasbih gone from the target.
            // (skip any still used by a routine step, which RESTRICT would reject)
            val gone = localTasbih.keys.filter {
                it !in targetTasbihIds && routineDao.countStepsUsingTasbih(it) == 0
            }
            if (gone.isNotEmpty()) tasbihDao.deleteByIds(gone)

            // Built-in favourites.
            val currentFavs = tasbihDao.getBuiltInFavoriteIds().toSet()
            val targetFavs = target.builtInFavorites.toSet()
            (currentFavs - targetFavs).forEach { tasbihDao.setFavorite(it, false) }
            (targetFavs - currentFavs).forEach { tasbihDao.setFavorite(it, true) }

            // Sessions.
            val localSessions = sessionDao.getAll()
            val keyed = localSessions.associateBy { sessionKey(BackupSession.from(it)) }
            val diff = diffSessions(keyed.keys, target.sessions, tasbihDao.getAllIds().toHashSet())
            val deleteIds = diff.toDeleteKeys.mapNotNull { keyed[it]?.id }
            if (deleteIds.isNotEmpty()) sessionDao.deleteByIds(deleteIds)
            diff.toInsert.forEach { sessionDao.insert(it.toEntity()) }
        }

        target.preferences.dailyGoalTarget?.let {
            preferencesRepository.setDailyGoalTarget(it.coerceIn(1, 99_999))
        }
    }
}
