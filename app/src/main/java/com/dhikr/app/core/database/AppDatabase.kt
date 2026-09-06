package com.dhikr.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dhikr.app.core.database.dao.RoutineCompletionDao
import com.dhikr.app.core.database.dao.RoutineDao
import com.dhikr.app.core.database.dao.RoutineProgressDao
import com.dhikr.app.core.database.dao.SessionDao
import com.dhikr.app.core.database.dao.TasbihDao
import com.dhikr.app.core.database.dao.TasbihProgressDao
import com.dhikr.app.core.database.entity.RoutineCompletionEntity
import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.core.database.entity.RoutineProgressEntity
import com.dhikr.app.core.database.entity.RoutineStepEntity
import com.dhikr.app.core.database.entity.SessionEntity
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.core.database.entity.TasbihProgressEntity

@Database(
    entities = [
        TasbihEntity::class,
        RoutineEntity::class,
        RoutineStepEntity::class,
        SessionEntity::class,
        RoutineCompletionEntity::class,
        RoutineProgressEntity::class,
        TasbihProgressEntity::class,
    ],
    // v2: no schema change from v1, but the version had been left at 1 across
    // several real schema edits (session/routine tables added without a bump).
    // Bumping now gives Room a version delta so an older on-device database
    // hits fallbackToDestructiveMigration (below) and is rebuilt+reseeded
    // instead of crashing the app on launch with an identity-hash mismatch.
    // v3: added routine_completion table (per-day "routine done" markers).
    // v4: renamed TasbihEntity.transliteration -> pronunciation. No hand
    // migration — fallbackToDestructiveMigration rebuilds + reseeds.
    // v5: added RoutineEntity.isFavorite (+ index). No hand migration —
    // fallbackToDestructiveMigration rebuilds + reseeds.
    // v6: added routine_progress table (per-day in-progress routine position).
    // No hand migration — fallbackToDestructiveMigration rebuilds + reseeds.
    // v7: added RoutineEntity.reminderEnabled / reminderMinuteOfDay /
    // reminderDays (per-routine local reminder). No hand migration —
    // fallbackToDestructiveMigration rebuilds + reseeds.
    // v8: added tasbih_progress table (per-day in-progress counting position).
    // No hand migration — fallbackToDestructiveMigration rebuilds + reseeds.
    // v9: added TasbihEntity.benefitsText / benefitsGeneratedAt (cached
    // Gemini-generated fada'il, per-tasbih). No hand migration —
    // fallbackToDestructiveMigration rebuilds + reseeds.
    // v10: added Index("routineId") on session (it is a SET_NULL foreign key
    // and was triggering a full-table-scan warning). No hand migration —
    // fallbackToDestructiveMigration rebuilds + reseeds.
    // v11: preset routines now seed with isFavorite = true (Home shows favorited
    // routines only). No schema change; bump reseeds so existing installs pick
    // up the new seed. No hand migration — fallbackToDestructiveMigration.
    // v12: added TasbihEntity.reminderEnabled / reminderMinuteOfDay /
    // reminderDays (per-tasbih local reminder, mirrors the v7 routine fields).
    // No hand migration — fallbackToDestructiveMigration rebuilds + reseeds.
    // v13: ~39 additional researched built-in dhikr in SeedData. No schema
    // change; bump reseeds so existing installs pick up the new list. No hand
    // migration — fallbackToDestructiveMigration rebuilds + reseeds.
    // v14: second batch of researched built-in dhikr from more_dhikr.md
    // (~120 entries: morning/evening, salah, occasions, travel, weather, social,
    // janazah/hajj adhkar; internal + existing-seed duplicates dropped,
    // weak-chain items flagged). No schema change; bump reseeds.
    // v15: removed the 10 weak/disputed-chain dhikr (⚠️-flagged) from SeedData
    // per authenticity review. No schema change; bump reseeds.
    // v16: added the 99 names of Allah (Asma-ul-Husna) as built-in tasbih
    // (asma_01..asma_99) plus a favorited 99-step "Asma-ul-Husna" preset
    // routine. No schema change; bump reseeds so existing installs pick them up.
    // No hand migration — fallbackToDestructiveMigration rebuilds + reseeds.
    // v17: added the 18 istighfar dhikr (istighfar_q_* Qur'anic + istighfar_*
    // sunnah) from lifewithallah's Istighfar page, plus a non-favorited
    // 19-step "Istighfar" preset routine. No schema change; bump reseeds.
    // No hand migration — fallbackToDestructiveMigration rebuilds + reseeds.
    // v18: added the 14 missing morning-adhkar dhikr (morn_*) from
    // lifewithallah's Morning page, and expanded the "Morning Dhikr" preset
    // routine from the 3-step stub to the full 24-step page sequence. No
    // schema change; bump reseeds. fallbackToDestructiveMigration.
    // v19: added the 8 evening-specific adhkar dhikr (eve_*) from
    // lifewithallah's Evening page, and expanded the "Evening Dhikr" preset
    // routine from the 3-step stub to the full 23-step page sequence. No
    // schema change; bump reseeds. fallbackToDestructiveMigration.
    // v20: added the 14 before-sleep dhikr (sleep_*) from lifewithallah's
    // Before Sleep page, and expanded the "Before Sleep" preset routine from
    // the 2-step stub to the full 20-step page sequence. No schema change;
    // bump reseeds. fallbackToDestructiveMigration.
    // v21: added the 25 praise dhikr (praise_*) from lifewithallah's Praises
    // of Allah page, plus a non-favorited 30-step "Praises of Allah" preset
    // routine. No schema change; bump reseeds. fallbackToDestructiveMigration.
    // v22: added the 27 Qur'anic-dua dhikr (qd_*) from lifewithallah's Quranic
    // Duas page, plus a non-favorited 41-step "Qur'anic Duas" preset routine.
    // No schema change; bump reseeds. fallbackToDestructiveMigration.
    // v23: added the 9 salawat dhikr (salawat_1..9) from lifewithallah's
    // Salawat page, plus a non-favorited 9-step "Salawat" preset routine. No
    // schema change; bump reseeds. fallbackToDestructiveMigration.
    // v24: added the 14 ruqyah/illness dhikr (ruq_*) from lifewithallah's
    // Ruqyah & Illness page, plus a non-favorited 20-step "Ruqyah & Illness"
    // preset routine. No schema change; bump reseeds. fallbackToDestructiveMigration.
    // v25: added 4 dhikr (bd_*) from the pastebin rewarded-dhikr list, plus a
    // non-favorited 14-step "Beneficial Dhikr" preset routine. No schema
    // change; bump reseeds. fallbackToDestructiveMigration.
    // v26: added Surah Al-Ikhlas / Al-Falaq / An-Nas as individual built-ins
    // (surah_ikhlas, surah_falaq, surah_nas) with full text, and gave
    // morn_3quls its full Arabic. No schema change; bump reseeds.
    version = 26,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasbihDao(): TasbihDao
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao
    abstract fun routineCompletionDao(): RoutineCompletionDao
    abstract fun routineProgressDao(): RoutineProgressDao
    abstract fun tasbihProgressDao(): TasbihProgressDao
}
