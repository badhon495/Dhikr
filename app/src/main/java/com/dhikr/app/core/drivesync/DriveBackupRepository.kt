package com.dhikr.app.core.drivesync

import android.content.Context
import com.dhikr.app.core.backup.BackupFormatException
import com.dhikr.app.core.backup.RestoreResult
import java.io.IOException

/** Name of the backup file this app creates/looks up on the user's Drive. */
private const val BACKUP_FILE_NAME = "dhikr_backup.json"

/** HTTP statuses that mean the access token is invalid/expired/unauthorized
 *  rather than a transient or server-side network problem. */
private val AUTH_FAILURE_STATUS_CODES = setOf(401, 403)

/**
 * Composes [DriveTokenProvider] (Task 4), [DriveApi] (Task 1) and
 * [DriveFileIdStore] (Task 2) with the app's existing backup JSON
 * export/restore logic to back up and restore this app's data to/from the
 * user's Google Drive.
 *
 * [exportJson] and [restoreJson] are plain suspend functions rather than a
 * direct `BackupRepository` dependency, so this class (and its tests) never
 * need a real `AppDatabase` — the caller (wired in a later task) supplies
 * `exportJson = { backupRepository.export(appVersionName) }` and
 * `restoreJson = backupRepository::restore`.
 */
class DriveBackupRepository(
    private val tokenProvider: DriveTokenProvider,
    private val driveApi: DriveApi,
    private val fileIdStore: DriveFileIdStore,
    private val exportJson: suspend () -> String,
    private val restoreJson: suspend (String) -> RestoreResult,
) {

    sealed interface BackupOutcome {
        data class Success(val bytes: Int) : BackupOutcome
        data class AuthFailed(val cause: Throwable) : BackupOutcome
        data class NetworkFailed(val cause: Throwable) : BackupOutcome
        data class ServerFailed(val cause: Throwable) : BackupOutcome
    }

    sealed interface RestoreOutcome {
        data class Success(val result: RestoreResult) : RestoreOutcome
        data object NoBackupFound : RestoreOutcome
        data class AuthFailed(val cause: Throwable) : RestoreOutcome
        data class NetworkFailed(val cause: Throwable) : RestoreOutcome
        data class ServerFailed(val cause: Throwable) : RestoreOutcome
        data class FormatInvalid(val message: String) : RestoreOutcome
    }

    suspend fun backup(activityContext: Context): BackupOutcome {
        val tokenResult = tokenProvider.getValidAccessToken(activityContext)
        var token = tokenResult.getOrElse { return BackupOutcome.AuthFailed(it) }

        val content = exportJson()

        return try {
            uploadBackup(token, content, allowFileIdFallback = true)
        } catch (e: DriveApi.DriveApiException) {
            if (e.statusCode in AUTH_FAILURE_STATUS_CODES) {
                // Token the provider handed us was rejected (expired/revoked
                // server-side even though our locally-computed TTL hadn't
                // elapsed yet). Clear it and retry once with a fresh one
                // before giving up.
                tokenProvider.invalidate()
                val retryToken = tokenProvider.getValidAccessToken(activityContext)
                    .getOrElse { return BackupOutcome.AuthFailed(it) }
                token = retryToken
                try {
                    uploadBackup(token, content, allowFileIdFallback = true)
                } catch (e2: DriveApi.DriveApiException) {
                    toBackupFailure(e2)
                } catch (e2: IOException) {
                    BackupOutcome.NetworkFailed(e2)
                }
            } else {
                toBackupFailure(e)
            }
        } catch (e: IOException) {
            BackupOutcome.NetworkFailed(e)
        }
    }

    private suspend fun uploadBackup(
        token: String,
        content: String,
        allowFileIdFallback: Boolean,
    ): BackupOutcome.Success {
        val fileId = resolveFileId(token)
        val resultId = try {
            driveApi.uploadOrUpdate(
                accessToken = token,
                existingFileId = fileId,
                fileName = BACKUP_FILE_NAME,
                jsonContent = content,
            )
        } catch (e: DriveApi.DriveApiException) {
            if (allowFileIdFallback && fileId != null && e.statusCode == STALE_FILE_ID_STATUS_CODE) {
                // The cached id pointed at a file that's gone (deleted/moved
                // in Drive) — clear it and fall back to a search by name
                // rather than misreporting this as a network failure.
                fileIdStore.clear()
                val found = driveApi.findFileId(token, BACKUP_FILE_NAME)
                if (found != null) {
                    fileIdStore.set(found)
                }
                val retryId = driveApi.uploadOrUpdate(
                    accessToken = token,
                    existingFileId = found,
                    fileName = BACKUP_FILE_NAME,
                    jsonContent = content,
                )
                fileIdStore.set(retryId)
                return BackupOutcome.Success(bytes = content.toByteArray(Charsets.UTF_8).size)
            }
            throw e
        }
        fileIdStore.set(resultId)
        return BackupOutcome.Success(bytes = content.toByteArray(Charsets.UTF_8).size)
    }

    suspend fun restore(activityContext: Context): RestoreOutcome {
        val tokenResult = tokenProvider.getValidAccessToken(activityContext)
        val token = tokenResult.getOrElse { return RestoreOutcome.AuthFailed(it) }

        return try {
            downloadBackup(token)
        } catch (e: DriveApi.DriveApiException) {
            if (e.statusCode in AUTH_FAILURE_STATUS_CODES) {
                tokenProvider.invalidate()
                val retryToken = tokenProvider.getValidAccessToken(activityContext)
                    .getOrElse { return RestoreOutcome.AuthFailed(it) }
                try {
                    downloadBackup(retryToken)
                } catch (e2: BackupFormatException) {
                    RestoreOutcome.FormatInvalid(e2.message ?: "This isn't a valid Dhikr backup file.")
                } catch (e2: DriveApi.DriveApiException) {
                    toRestoreFailure(e2)
                } catch (e2: IOException) {
                    RestoreOutcome.NetworkFailed(e2)
                }
            } else {
                toRestoreFailure(e)
            }
        } catch (e: BackupFormatException) {
            RestoreOutcome.FormatInvalid(e.message ?: "This isn't a valid Dhikr backup file.")
        } catch (e: IOException) {
            RestoreOutcome.NetworkFailed(e)
        }
    }

    private suspend fun downloadBackup(token: String): RestoreOutcome {
        val cached = fileIdStore.get()
        val fileId = cached ?: run {
            val found = driveApi.findFileId(token, BACKUP_FILE_NAME)
            if (found != null) {
                fileIdStore.set(found)
            }
            found
        } ?: return RestoreOutcome.NoBackupFound

        val content = try {
            driveApi.downloadContent(token, fileId)
        } catch (e: DriveApi.DriveApiException) {
            if (cached != null && e.statusCode == STALE_FILE_ID_STATUS_CODE) {
                // Cached id is stale (file deleted/moved in Drive) — clear it
                // and fall back to a fresh search instead of reporting a
                // misleading network failure.
                fileIdStore.clear()
                val found = driveApi.findFileId(token, BACKUP_FILE_NAME)
                    ?: return RestoreOutcome.NoBackupFound
                fileIdStore.set(found)
                driveApi.downloadContent(token, found)
            } else {
                throw e
            }
        }
        val result = restoreJson(content)
        return RestoreOutcome.Success(result)
    }

    /** Returns the file id to use: the cached one if present, otherwise a
     *  Drive search by name (covers a file from a previous install/session
     *  whose id cache was cleared locally). Whatever id is found this way is
     *  cached back into [fileIdStore]. Returns null if no id is cached and
     *  the search finds nothing. */
    private suspend fun resolveFileId(token: String): String? {
        val cached = fileIdStore.get()
        if (cached != null) {
            return cached
        }
        val found = driveApi.findFileId(token, BACKUP_FILE_NAME)
        if (found != null) {
            fileIdStore.set(found)
        }
        return found
    }

    private fun toBackupFailure(e: DriveApi.DriveApiException): BackupOutcome =
        when {
            e.statusCode in AUTH_FAILURE_STATUS_CODES -> BackupOutcome.AuthFailed(e)
            e.statusCode == -1 -> BackupOutcome.NetworkFailed(e)
            else -> BackupOutcome.ServerFailed(e)
        }

    private fun toRestoreFailure(e: DriveApi.DriveApiException): RestoreOutcome =
        when {
            e.statusCode in AUTH_FAILURE_STATUS_CODES -> RestoreOutcome.AuthFailed(e)
            e.statusCode == -1 -> RestoreOutcome.NetworkFailed(e)
            else -> RestoreOutcome.ServerFailed(e)
        }

    private companion object {
        /** `DriveApi` reports a connectivity-level `IOException` as status
         *  -1 (see `defaultHttpCall`); treat only that as "no connection",
         *  distinct from a genuine HTTP error status from Drive. */
        const val STALE_FILE_ID_STATUS_CODE = 404
    }
}
