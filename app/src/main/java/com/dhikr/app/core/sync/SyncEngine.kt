package com.dhikr.app.core.sync

import kotlinx.coroutines.sync.Mutex

data class RemoteFile(val content: String, val etag: String?)

/** One cloud account's app-folder file. */
interface CloudProvider {
    /** The cloud file, or null if it doesn't exist yet. */
    suspend fun download(): RemoteFile?

    /** Writes the file and returns its new etag.
     *  @throws SyncException CONFLICT when [ifMatch] no longer matches the cloud file. */
    suspend fun upload(content: String, ifMatch: String?): String
}

/** Local sync artifacts: the last-synced base and the pre-sync snapshot used by undo. */
interface SyncFiles {
    fun readBase(): String?
    fun writeBase(text: String)
    fun deleteBase()
    fun readSnapshot(): String?
    fun writeSnapshot(text: String)
    fun deleteSnapshot()
}

enum class UndoResult { DONE, NOTHING_TO_UNDO, CHANGED_SINCE }

data class SyncSummary(val localChanges: Int, val remoteChanges: Int)

class SyncEngine(
    private val local: SyncLocal,
    private val files: SyncFiles,
) {
    private val lock = Mutex()

    /** @throws SyncException (BUSY if a sync is already running). */
    suspend fun sync(provider: CloudProvider): SyncSummary {
        if (!lock.tryLock()) throw SyncException(SyncException.Kind.BUSY, "A sync is already running")
        try {
            return run(provider)
        } finally {
            lock.unlock()
        }
    }

    private suspend fun run(provider: CloudProvider): SyncSummary {
        var localChanges = 0
        var remoteChanges = 0
        for (attempt in 0..1) {
            val remoteFile = provider.download()
            val remote = remoteFile?.let { SyncCodec.decode(it.content) }
            val localState = local.readState()
            val base = files.readBase()?.let { runCatching { SyncCodec.decode(it) }.getOrNull() }

            val result = mergeState(localState, remote, base)
            // Only the first pass sees the true pre-sync state; a retry has already applied it.
            if (attempt == 0) {
                files.writeSnapshot(SyncCodec.encode(localState))
                localChanges = result.localChanges
                remoteChanges = result.remoteChanges
            }
            local.applyState(result.merged)

            val encoded = SyncCodec.encode(result.merged)
            if (remote == null || result.remoteChanges > 0) {
                try {
                    provider.upload(encoded, remoteFile?.etag)
                } catch (e: SyncException) {
                    if (e.kind == SyncException.Kind.CONFLICT && attempt == 0) continue
                    throw e
                }
            }
            // The base is what this device actually holds after applying, not the merged
            // target: anything the device could not apply (e.g. a routine using a built-in
            // it lacks) stays "new on the cloud" next time instead of looking deleted here.
            files.writeBase(SyncCodec.encode(local.readState()))
            return SyncSummary(localChanges, remoteChanges)
        }
        throw SyncException(SyncException.Kind.CONFLICT, "Cloud file kept changing")
    }

    /** Restores this device to the state before the last sync. The cloud is not touched;
     *  the base is dropped, so the next sync re-applies cloud changes (union, no deletes).
     *  Refused once anything has changed on this device since that sync, so undo can never
     *  delete newer counting history. The snapshot is single-use.
     *  @throws SyncException BUSY while a sync or another undo is running. */
    suspend fun undoLast(): UndoResult {
        if (!lock.tryLock()) throw SyncException(SyncException.Kind.BUSY, "A sync is already running")
        try {
            val snapshot = files.readSnapshot() ?: return UndoResult.NOTHING_TO_UNDO
            val base = files.readBase()?.let { runCatching { SyncCodec.decode(it) }.getOrNull() }
            if (base != null && !sameSyncedState(local.readState(), base)) return UndoResult.CHANGED_SINCE
            local.applyState(SyncCodec.decode(snapshot))
            files.deleteBase()
            files.deleteSnapshot()
            return UndoResult.DONE
        } finally {
            lock.unlock()
        }
    }

    /** Forget sync history (provider switch / disconnect): base and undo snapshot. */
    fun resetBase() {
        files.deleteBase()
        files.deleteSnapshot()
    }

    fun hasSnapshot(): Boolean = files.readSnapshot() != null
}
