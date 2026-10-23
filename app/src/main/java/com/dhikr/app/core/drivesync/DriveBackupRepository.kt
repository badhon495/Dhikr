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
    }

    sealed interface RestoreOutcome {
        data class Success(val result: RestoreResult) : RestoreOutcome
        data object NoBackupFound : RestoreOutcome
        data class AuthFailed(val cause: Throwable) : RestoreOutcome
        data class NetworkFailed(val cause: Throwable) : RestoreOutcome
        data class FormatInvalid(val message: String) : RestoreOutcome
    }

    suspend fun backup(activityContext: Context): BackupOutcome {
        val tokenResult = tokenProvider.getValidAccessToken(activityContext)
        val token = tokenResult.getOrElse { return BackupOutcome.AuthFailed(it) }

        val content = exportJson()

        return try {
            val fileId = resolveFileId(token)
            val resultId = driveApi.uploadOrUpdate(
                accessToken = token,
                existingFileId = fileId,
                fileName = BACKUP_FILE_NAME,
                jsonContent = content,
            )
            fileIdStore.set(resultId)
            BackupOutcome.Success(bytes = content.toByteArray(Charsets.UTF_8).size)
        } catch (e: DriveApi.DriveApiException) {
            toBackupFailure(e)
        } catch (e: IOException) {
            BackupOutcome.NetworkFailed(e)
        }
    }

    suspend fun restore(activityContext: Context): RestoreOutcome {
        val tokenResult = tokenProvider.getValidAccessToken(activityContext)
        val token = tokenResult.getOrElse { return RestoreOutcome.AuthFailed(it) }

        return try {
            val fileId = resolveFileId(token) ?: return RestoreOutcome.NoBackupFound
            val content = driveApi.downloadContent(token, fileId)
            val result = restoreJson(content)
            RestoreOutcome.Success(result)
        } catch (e: BackupFormatException) {
            RestoreOutcome.FormatInvalid(e.message ?: "This isn't a valid Dhikr backup file.")
        } catch (e: DriveApi.DriveApiException) {
            toRestoreFailure(e)
        } catch (e: IOException) {
            RestoreOutcome.NetworkFailed(e)
        }
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
        if (e.statusCode in AUTH_FAILURE_STATUS_CODES) {
            BackupOutcome.AuthFailed(e)
        } else {
            BackupOutcome.NetworkFailed(e)
        }

    private fun toRestoreFailure(e: DriveApi.DriveApiException): RestoreOutcome =
        if (e.statusCode in AUTH_FAILURE_STATUS_CODES) {
            RestoreOutcome.AuthFailed(e)
        } else {
            RestoreOutcome.NetworkFailed(e)
        }
}
