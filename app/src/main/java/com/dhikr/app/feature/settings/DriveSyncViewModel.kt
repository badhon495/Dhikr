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
import kotlinx.coroutines.CancellationException
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
 *
 * Every [viewModelScope] launch below wraps its body in a catch-all, same
 * pattern as [BackupViewModel]: an unmapped exception (a Room/SQLite error
 * from `exportJson`/`restoreJson`, a `SerializationException` from unexpected
 * JSON, a non-`ApiException` from `authorize()`, or a cancelled/failed
 * authorization `Task` resuming with `CancellationException`) must map to
 * [Status.Error] rather than crash the app or leave [status] stuck at
 * [Status.Working] forever.
 */
class DriveSyncViewModel(
    private val authManager: DriveAuthManager,
    private val backupRepository: DriveBackupRepository,
    /** Called after a Drive backup succeeds. */
    private val onBackedUp: suspend () -> Unit = {},
) : ViewModel() {

    /** Why a [Status.Error] happened, so the UI can show a precise, distinct
     *  message instead of a generic one. */
    enum class ErrorKind { GENERIC, NETWORK, AUTH, SERVER }

    sealed interface Status {
        data object Idle : Status
        data object Working : Status
        data class BackupDone(val bytes: Int) : Status
        data class RestoreDone(val result: RestoreResult) : Status
        data object NoBackupFound : Status
        data class AuthorizationResolutionRequired(val pendingIntent: PendingIntent) : Status
        data class Error(val message: String, val kind: ErrorKind = ErrorKind.GENERIC) : Status
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
            try {
                _status.value = withContext(Dispatchers.IO) {
                    authManager.signIn(activityContext)
                }.fold(
                    onSuccess = { Status.Idle },
                    onFailure = { cause ->
                        if (authManager.isSignInCancellation(cause)) {
                            // User backed out of the Credential Manager
                            // dialog; that's not a failure worth surfacing.
                            Status.Idle
                        } else {
                            Status.Error(cause.message ?: "Sign-in failed.")
                        }
                    },
                )
            } catch (e: CancellationException) {
                _status.value = Status.Idle
            } catch (e: Exception) {
                _status.value = Status.Error(e.message ?: "Sign-in failed.")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    authManager.signOut()
                }
                _status.value = Status.Idle
            } catch (e: Exception) {
                _status.value = Status.Error(e.message ?: "Sign-out failed.")
            }
        }
    }

    fun backup(activityContext: Context) {
        if (_status.value == Status.Working) return
        _status.value = Status.Working
        viewModelScope.launch {
            try {
                _status.value = when (val outcome = withContext(Dispatchers.IO) {
                    backupRepository.backup(activityContext)
                }) {
                    is DriveBackupRepository.BackupOutcome.Success -> {
                        runCatching { onBackedUp() }
                        Status.BackupDone(outcome.bytes)
                    }
                    is DriveBackupRepository.BackupOutcome.AuthFailed -> authFailureStatus(outcome.cause)
                    is DriveBackupRepository.BackupOutcome.NetworkFailed ->
                        Status.Error(
                            networkErrorMessage(),
                            kind = ErrorKind.NETWORK,
                        )
                    is DriveBackupRepository.BackupOutcome.ServerFailed ->
                        Status.Error(
                            serverErrorMessage(),
                            kind = ErrorKind.SERVER,
                        )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = Status.Error(e.message ?: "Backup failed.")
            }
        }
    }

    fun restore(activityContext: Context) {
        if (_status.value == Status.Working) return
        _status.value = Status.Working
        viewModelScope.launch {
            try {
                _status.value = when (val outcome = withContext(Dispatchers.IO) {
                    backupRepository.restore(activityContext)
                }) {
                    is DriveBackupRepository.RestoreOutcome.Success -> Status.RestoreDone(outcome.result)
                    is DriveBackupRepository.RestoreOutcome.NoBackupFound -> Status.NoBackupFound
                    is DriveBackupRepository.RestoreOutcome.AuthFailed -> authFailureStatus(outcome.cause)
                    is DriveBackupRepository.RestoreOutcome.NetworkFailed ->
                        Status.Error(
                            networkErrorMessage(),
                            kind = ErrorKind.NETWORK,
                        )
                    is DriveBackupRepository.RestoreOutcome.ServerFailed ->
                        Status.Error(
                            serverErrorMessage(),
                            kind = ErrorKind.SERVER,
                        )
                    is DriveBackupRepository.RestoreOutcome.FormatInvalid -> Status.Error(outcome.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = Status.Error(e.message ?: "Restore failed.")
            }
        }
    }

    /** `AuthFailed` outcomes wrap whatever [DriveAuthManager.getValidAccessToken]
     *  returned; when that failure is specifically a consent-required signal,
     *  surface it as its own [Status] so the UI can launch the resolution
     *  [PendingIntent] and retry, rather than showing a dead-end error.
     *  Otherwise — including the case where [DriveBackupRepository] already
     *  retried once after invalidating a rejected token and still got an
     *  auth failure — surface the dedicated auth-error message rather than
     *  the raw `cause.message`, which is usually non-null and so would never
     *  let the string-resource fallback below actually fire. */
    private fun authFailureStatus(cause: Throwable): Status =
        if (cause is DriveAuthorizationResolutionRequired && cause.pendingIntent != null) {
            Status.AuthorizationResolutionRequired(cause.pendingIntent)
        } else {
            Status.Error(authErrorMessage(), kind = ErrorKind.AUTH)
        }

    /** Placeholder raw text; [DriveSyncControls] substitutes the localized
     *  string resource for [ErrorKind.AUTH] instead of displaying this. */
    private fun authErrorMessage() = "Drive sign-in expired. Please sign in again."

    /** Placeholder raw text; [DriveSyncControls] substitutes the localized
     *  string resource for [ErrorKind.NETWORK] instead of displaying this. */
    private fun networkErrorMessage() = "No connection. Check your internet and try again."

    /** Placeholder raw text; [DriveSyncControls] substitutes the localized
     *  string resource for [ErrorKind.SERVER] instead of displaying this. */
    private fun serverErrorMessage() = "Google Drive had a problem. Try again later."

    class Factory(
        private val authManager: DriveAuthManager,
        private val backupRepository: DriveBackupRepository,
        private val onBackedUp: suspend () -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DriveSyncViewModel(authManager, backupRepository, onBackedUp) as T
    }
}
