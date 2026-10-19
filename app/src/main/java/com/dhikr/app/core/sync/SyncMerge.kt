package com.dhikr.app.core.sync

import com.dhikr.app.core.backup.BackupFile
import com.dhikr.app.core.backup.BackupPreferences
import com.dhikr.app.core.backup.BackupRoutine
import com.dhikr.app.core.backup.BackupSession
import com.dhikr.app.core.backup.BackupTasbih
import com.dhikr.app.core.backup.SYNC_FORMAT
import com.dhikr.app.core.backup.SYNC_VERSION

/** Identity of a session across devices; sessions have no shared id. */
fun sessionKey(s: BackupSession): String = "${s.tasbihId}|${s.startedAt}|${s.endedAt}|${s.count}"

data class MergeResult(
    val merged: BackupFile,
    /** Items the merge changes on this device (upserts, deletes, new sessions). */
    val localChanges: Int,
    /** Items the merge changes in the cloud file. */
    val remoteChanges: Int,
)

data class SessionDiff(val toInsert: List<BackupSession>, val toDeleteKeys: Set<String>)

/**
 * Three-way merge of [local] and [remote] against [base] (the merged state saved
 * after the previous successful sync). [remote] null = no cloud file yet; [base]
 * null = never synced. Without a usable base nothing is ever deleted.
 */
fun mergeState(local: BackupFile, remote: BackupFile?, base: BackupFile?): MergeResult {
    // A missing cloud file must not be read as "everything was deleted remotely".
    val effectiveBase = if (remote == null) null else base

    val mergedTasbih = mergeById(
        local.customTasbih, remote?.customTasbih, effectiveBase?.customTasbih,
        id = { it.id }, updatedAt = { it.updatedAt },
    ).toMutableList()
    val mergedRoutines = mergeById(
        local.customRoutines, remote?.customRoutines, effectiveBase?.customRoutines,
        id = { it.id }, updatedAt = { it.updatedAt },
    )

    // A tasbih still used by a surviving routine step must not be deleted (FK RESTRICT).
    val known = (local.customTasbih + remote?.customTasbih.orEmpty()).associateBy { it.id }
    val mergedIds = mergedTasbih.map { it.id }.toSet()
    val referenced = mergedRoutines.flatMap { r -> r.steps.map { it.tasbihId } }.toSet()
    for (id in referenced) {
        if (id !in mergedIds) known[id]?.let { mergedTasbih += it }
    }
    val finalIds = mergedTasbih.map { it.id }.toSet()
    val deletedIds = known.keys - finalIds

    val favourites = mergeFavourites(
        local.builtInFavorites, remote?.builtInFavorites, effectiveBase?.builtInFavorites,
    )
    val goal = mergeGoal(
        local.preferences.dailyGoalTarget,
        remote?.preferences?.dailyGoalTarget,
        effectiveBase?.preferences?.dailyGoalTarget,
        hasBase = effectiveBase != null,
    )

    val sessions = (local.sessions + remote?.sessions.orEmpty())
        .filter { it.tasbihId !in deletedIds }
        .distinctBy(::sessionKey)
        .sortedWith(compareBy({ it.startedAt }, { it.tasbihId }, { it.endedAt }))

    val merged = BackupFile(
        format = SYNC_FORMAT,
        version = SYNC_VERSION,
        createdAt = maxOf(local.createdAt, remote?.createdAt ?: 0L),
        appVersionName = local.appVersionName,
        customTasbih = mergedTasbih.sortedBy { it.id },
        builtInFavorites = favourites.sorted(),
        customRoutines = mergedRoutines.sortedBy { it.id },
        sessions = sessions,
        preferences = BackupPreferences(dailyGoalTarget = goal),
    )
    return MergeResult(
        merged = merged,
        localChanges = changeCount(merged, local),
        remoteChanges = changeCount(merged, remote ?: BackupFile()),
    )
}

/** Sessions to insert/delete so a device holding [localKeys] ends up equal to [target],
 *  ignoring target sessions whose tasbih doesn't exist on this device. */
fun diffSessions(
    localKeys: Set<String>,
    target: List<BackupSession>,
    validTasbihIds: Set<String>,
): SessionDiff {
    val targetKeys = target.mapTo(HashSet(), ::sessionKey)
    val inserts = target.filter { sessionKey(it) !in localKeys && it.tasbihId in validTasbihIds }
    return SessionDiff(toInsert = inserts, toDeleteKeys = localKeys - targetKeys)
}

private fun <T> mergeById(
    local: List<T>,
    remote: List<T>?,
    base: List<T>?,
    id: (T) -> String,
    updatedAt: (T) -> Long,
): List<T> {
    val l = local.associateBy(id)
    val r = remote.orEmpty().associateBy(id)
    val b = base?.associateBy(id)
    val out = ArrayList<T>()
    for (key in l.keys + r.keys) {
        val lv = l[key]
        val rv = r[key]
        val bv = b?.get(key)
        val pick: T? = when {
            lv != null && rv != null -> when {
                lv == rv -> lv
                bv != null && lv == bv -> rv
                bv != null && rv == bv -> lv
                updatedAt(lv) > updatedAt(rv) -> lv
                else -> rv
            }
            // Only local has it: deleted remotely if it was in base — unless edited here.
            lv != null -> if (bv != null && lv == bv) null else lv
            // Only remote has it: deleted locally if it was in base — unless edited there.
            rv != null -> if (bv != null && rv == bv) null else rv
            else -> null
        }
        if (pick != null) out += pick
    }
    return out
}

private fun mergeFavourites(local: List<String>, remote: List<String>?, base: List<String>?): Set<String> {
    val l = local.toSet()
    val r = remote.orEmpty().toSet()
    if (base == null) return l + r
    val b = base.toSet()
    return (l + r + b).filterTo(HashSet()) { id ->
        val inL = id in l
        val inR = id in r
        val inB = id in b
        if (inL == inR) inL else if (inL != inB) inL else inR
    }
}

private fun mergeGoal(local: Int?, remote: Int?, base: Int?, hasBase: Boolean): Int? = when {
    remote == null -> local
    hasBase && base != null -> if (local == base) remote else local
    else -> remote
}

private fun changeCount(merged: BackupFile, other: BackupFile): Int {
    var n = 0
    n += symmetricDiff(merged.customTasbih, other.customTasbih, BackupTasbih::id)
    n += symmetricDiff(merged.customRoutines, other.customRoutines, BackupRoutine::id)
    n += (merged.builtInFavorites.toSet() - other.builtInFavorites.toSet()).size +
        (other.builtInFavorites.toSet() - merged.builtInFavorites.toSet()).size
    val mk = merged.sessions.mapTo(HashSet(), ::sessionKey)
    val ok = other.sessions.mapTo(HashSet(), ::sessionKey)
    n += mk.count { it !in ok } + ok.count { it !in mk }
    val g = merged.preferences.dailyGoalTarget
    if (g != null && g != other.preferences.dailyGoalTarget) n++
    return n
}

/** Items present in one list but not the other, or present in both but different. */
private fun <T> symmetricDiff(a: List<T>, b: List<T>, id: (T) -> String): Int {
    val am = a.associateBy(id)
    val bm = b.associateBy(id)
    return (am.keys + bm.keys).count { am[it] != bm[it] }
}
