package com.dhikr.app.feature.settings

import android.app.PendingIntent
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.backup.RestoreResult
import com.dhikr.app.core.drivesync.DriveAuthManager
import com.dhikr.app.core.drivesync.DriveAuthorizationResolutionRequired
import com.dhikr.app.core.drivesync.DriveBackupRepository
import com.dhikr.app.core.drivesync.SignedInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the Drive Sync section of Settings: sign-in/out via [DriveAuthManager]
 * and backup/restore via [DriveBackupRepository]. Mirrors [BackupViewModel]'s
 * structure (status flow gated on [Status.Working], IO dispatch around
 * repository calls, a [Factory]).
 */
class DriveSyncViewModel(
    private val authManager: DriveAuthManager,
    private val backupRepository: DriveBackupRepository,
) : ViewModel() {

    sealed interface Status {
        data object Idle : Status
        data object Working : Status
        data class BackupDone(val bytes: Int) : Status
        data class RestoreDone(val result: RestoreResult) : Status
        data object NoBackupFound : Status
        data class AuthorizationResolutionRequired(val pendingIntent: PendingIntent) : Status
        data class Error(val message: String) : Status
    }

    val signedInAccount: StateFlow<SignedInAccount?> = authManager.signedInAccount

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun clearStatus() {
        _status.value = Status.Idle
    }

    fun signIn(activityContext: Context) {
        if (_status.value == Status.Working) return
        _status.value = Status.Working
        viewModelScope.launch {
            _status.value = withContext(Dispatchers.IO) {
                authManager.signIn(activityContext)
            }.fold(
                onSuccess = { Status.Idle },
                onFailure = { Status.Error(it.message ?: "Sign-in failed.") },
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                authManager.signOut()
            }
            _status.value = Status.Idle
        }
    }

    fun backup(activityContext: Context) {
        if (_status.value == Status.Working) return
        _status.value = Status.Working
        viewModelScope.launch {
            _status.value = when (val outcome = withContext(Dispatchers.IO) {
                backupRepository.backup(activityContext)
            }) {
                is DriveBackupRepository.BackupOutcome.Success -> Status.BackupDone(outcome.bytes)
                is DriveBackupRepository.BackupOutcome.AuthFailed -> authFailureStatus(outcome.cause)
                is DriveBackupRepository.BackupOutcome.NetworkFailed ->
                    Status.Error(outcome.cause.message ?: "Backup failed. Check your connection.")
            }
        }
    }

    fun restore(activityContext: Context) {
        if (_status.value == Status.Working) return
        _status.value = Status.Working
        viewModelScope.launch {
            _status.value = when (val outcome = withContext(Dispatchers.IO) {
                backupRepository.restore(activityContext)
            }) {
                is DriveBackupRepository.RestoreOutcome.Success -> Status.RestoreDone(outcome.result)
                is DriveBackupRepository.RestoreOutcome.NoBackupFound -> Status.NoBackupFound
                is DriveBackupRepository.RestoreOutcome.AuthFailed -> authFailureStatus(outcome.cause)
                is DriveBackupRepository.RestoreOutcome.NetworkFailed ->
                    Status.Error(outcome.cause.message ?: "Restore failed. Check your connection.")
                is DriveBackupRepository.RestoreOutcome.FormatInvalid -> Status.Error(outcome.message)
            }
        }
    }

    /** `AuthFailed` outcomes wrap whatever [DriveAuthManager.getValidAccessToken]
     *  returned; when that failure is specifically a consent-required signal,
     *  surface it as its own [Status] so the UI can launch the resolution
     *  [PendingIntent] and retry, rather than showing a dead-end error. */
    private fun authFailureStatus(cause: Throwable): Status =
        if (cause is DriveAuthorizationResolutionRequired && cause.pendingIntent != null) {
            Status.AuthorizationResolutionRequired(cause.pendingIntent)
        } else {
            Status.Error(cause.message ?: "Drive sign-in expired. Please sign in again.")
        }

    class Factory(
        private val authManager: DriveAuthManager,
        private val backupRepository: DriveBackupRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DriveSyncViewModel(authManager, backupRepository) as T
    }
}
