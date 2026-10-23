package com.dhikr.app

import android.app.Application
import androidx.room.Room
import com.dhikr.app.core.database.AppDatabase
import com.dhikr.app.core.database.DuaRepository
import com.dhikr.app.core.database.seed.SeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DhikrApplication : Application() {

    // Outlives every ViewModel/Activity. Used for writes that must finish even
    // though the scope that started them is being torn down — e.g. logging the
    // in-progress counter session to History when the Counter screen's
    // NavBackStackEntry (and its viewModelScope) is popped on back navigation.
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "dhikr.db")
            // v29->v30 added the `dua` table, which can hold genuinely
            // user-authored rows (added/edited through the Dua editor) by the
            // time a device upgrades past it — unlike every earlier bump,
            // those rows aren't reseedable. The hand migration creates the
            // table in place so this specific bump doesn't fall through to
            // the destructive path below and drop the other tables.
            .addMigrations(AppDatabase.MIGRATION_29_30)
            // The DB otherwise carries only seeded/derived data (built-in
            // dhikr, presets, session history) — all rebuildable. If a
            // restored backup or an old install leaves a schema Room can't
            // reconcile, wipe and reseed rather than crash on launch.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        com.dhikr.app.core.notifications.ReminderNotifications.ensureChannel(this)
        // B7: the seed check/insert are suspend Room calls — dispatch on IO,
        // not Default. Room runs the actual SQLite work on its own executor,
        // but the coroutine still shouldn't sit on a CPU-bound dispatcher.
        applicationScope.launch(Dispatchers.IO) {
            // IGNORE conflict strategy: existing rows (by id) are untouched, so
            // this is safe to run on every launch and syncs newly-added
            // built-in dhikr into installs that already seeded the DB.
            database.tasbihDao().insertAll(SeedData.builtInTasbih)
            // Built-ins aren't user-editable, so overwriting their seed text is
            // safe and carries seed-text corrections to existing installs
            // without a destructive version bump.
            database.tasbihDao().refreshBuiltInTexts(SeedData.builtInTasbih)
            if (database.routineDao().count() == 0) {
                database.routineDao().insertRoutines(SeedData.presetRoutines)
                database.routineDao().insertSteps(SeedData.presetRoutineSteps)
            }
            // Seed daily-life dua entries on first run.
            DuaRepository(database.duaDao()).seedIfEmpty()
            syncRoutineStepAdditions()
            purgeRetiredTasbih()
        }
    }

    /**
     * One-time: append the later-added Morning/Evening steps
     * ([SeedData.routineStepAdditions]) to installs that seeded the routines
     * before those steps existed. Skips tasbih already in the routine. Runs
     * once so steps the user later removes don't come back.
     */
    private suspend fun syncRoutineStepAdditions() {
        val prefs = getSharedPreferences("seed_sync", MODE_PRIVATE)
        val key = "routine_step_additions_v1"
        if (prefs.getBoolean(key, false)) return
        val routineDao = database.routineDao()
        for ((routineId, additions) in SeedData.routineStepAdditions) {
            val routine = routineDao.getWithSteps(routineId) ?: continue
            val present = routine.steps.map { it.tasbihId }.toSet()
            var order = (routine.steps.maxOfOrNull { it.stepOrder } ?: -1) + 1
            val toAdd = additions.filter { (id, _) -> id !in present }.map { (id, count) ->
                com.dhikr.app.core.database.entity.RoutineStepEntity(
                    routineId = routineId, tasbihId = id, stepOrder = order++, targetCount = count,
                )
            }
            if (toAdd.isNotEmpty()) routineDao.insertSteps(toAdd)
        }
        prefs.edit().putBoolean(key, true).apply()
    }

    /**
     * Drop the retired situational built-in dhikr, and any built-in row not in
     * the current seed, from an existing database (fresh installs never seed them). Keeps any the user favourited, and any a
     * routine step still references — routine_step -> tasbih is RESTRICT, so
     * deleting a referenced row would throw.
     */
    private suspend fun purgeRetiredTasbih() {
        val tasbihDao = database.tasbihDao()
        val routineDao = database.routineDao()
        val favourites = tasbihDao.getBuiltInFavoriteIds().toSet()
        // Retired ids plus any built-in row the current seed no longer ships
        // (stale leftovers from older seed versions show up as duplicates).
        // Custom tasbih (isBuiltIn = 0) are never in getBuiltInIds().
        val seedIds = SeedData.builtInTasbih.mapTo(HashSet()) { it.id }
        val stale = tasbihDao.getBuiltInIds().filter { it !in seedIds }
        val purgeable = (SeedData.retiredBuiltInIds + stale)
            .filter { it !in favourites && routineDao.countStepsUsingTasbih(it) == 0 }
        // Chunked to stay under SQLite's bound-variable limit.
        purgeable.chunked(500).forEach { tasbihDao.deleteByIds(it) }
    }
}
