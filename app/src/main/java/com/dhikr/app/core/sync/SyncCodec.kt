package com.dhikr.app.core.sync

import com.dhikr.app.core.backup.BackupFile
import com.dhikr.app.core.backup.SYNC_FORMAT
import com.dhikr.app.core.backup.SYNC_VERSION
import kotlinx.serialization.json.Json

/** Failure of a sync step; [kind] drives the user-facing message. */
class SyncException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind { NOT_CONNECTED, AUTH_EXPIRED, NETWORK, QUOTA, NEWER_FORMAT, CORRUPT, CONFLICT, BUSY, UNKNOWN }
}

/** Reads and writes the cloud file (dhikr-sync.json), which shares [BackupFile]'s shape. */
object SyncCodec {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(file: BackupFile): String =
        json.encodeToString(BackupFile.serializer(), file.copy(format = SYNC_FORMAT, version = SYNC_VERSION))

    /** @throws SyncException CORRUPT if unreadable/wrong format, NEWER_FORMAT if from a newer app. */
    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw SyncException(SyncException.Kind.CORRUPT, "Cloud file is not readable", e)
        }
        if (file.format != SYNC_FORMAT) {
            throw SyncException(SyncException.Kind.CORRUPT, "Cloud file has an unknown format")
        }
        if (file.version > SYNC_VERSION) {
            throw SyncException(SyncException.Kind.NEWER_FORMAT, "Cloud file is from a newer app version")
        }
        return file
    }
}
