package com.dhikr.app.core.drivesync

import android.content.Context
import com.dhikr.app.BuildConfig
import com.dhikr.app.core.backup.BackupRepository

/** Builds the Drive auth + backup objects; shared by Settings and the
 *  on-launch auto backup so both wire them identically. */
class DriveSyncGraph(
    val authManager: DriveAuthManager,
    val backupRepository: DriveBackupRepository,
) {
    companion object {
        fun create(
            context: Context,
            localBackup: BackupRepository,
            appVersionName: String,
        ): DriveSyncGraph {
            val appContext = context.applicationContext
            val fileIdStore = DriveFileIdStore(appContext.driveSyncDataStore)
            val authManager = DriveAuthManager(
                context = appContext,
                tokenStore = DriveTokenStore(appContext),
                fileIdStore = fileIdStore,
                webClientId = BuildConfig.DRIVE_WEB_CLIENT_ID,
                androidClientId = BuildConfig.DRIVE_ANDROID_CLIENT_ID,
            )
            val repo = DriveBackupRepository(
                tokenProvider = authManager,
                driveApi = DriveApi(),
                fileIdStore = fileIdStore,
                exportJson = { localBackup.export(appVersionName) },
                restoreJson = localBackup::restore,
            )
            return DriveSyncGraph(authManager, repo)
        }
    }
}
