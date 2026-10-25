package com.dhikr.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.backup.BackupFormatException
import com.dhikr.app.core.backup.BackupRepository
import com.dhikr.app.core.backup.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the Backup section of Settings. The screen owns the Storage Access
 * Framework dialogs and hands this ViewModel plain read/write lambdas over the
 * chosen file, so the ViewModel never touches a Context or a Uri.
 */
class BackupViewModel(
    private val repository: BackupRepository,
    private val appVersionName: String,
    lastBackupAtFlow: Flow<Long> = flowOf(0L),
    /** Called after a full-backup file export succeeds (not the CSV one). */
    private val onBackedUp: suspend () -> Unit = {},
) : ViewModel() {

    /** Epoch millis of the last successful backup of any kind; 0 = never. */
    val lastBackupAt: StateFlow<Long> =
        lastBackupAtFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    sealed interface Status {
        data object Idle : Status
        data object Working : Status
        data class ExportDone(val bytes: Int) : Status
        data class CsvDone(val bytes: Int) : Status
        data class RestoreDone(val result: RestoreResult) : Status
        data class Error(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun clearStatus() {
        _status.value = Status.Idle
    }

    /** [writeBytes] persists the backup text to the file the user picked. */
    fun export(writeBytes: suspend (ByteArray) -> Unit) {
        if (_status.value == Status.Working) return
        _status.value = Status.Working
        viewModelScope.launch {
            _status.value = try {
                val bytes = withContext(Dispatchers.IO) {
                    val text = repository.export(appVersionName)
                    val data = text.toByteArray(Charsets.UTF_8)
                    writeBytes(data)
                    data.size
                }
                runCatching { onBackedUp() }
                Status.ExportDone(bytes)
            } catch (e: Exception) {
                Status.Error("Couldn't write the backup file.")
            }
        }
    }

    /** History as CSV; same flow as [export], to a file the user picked. */
    fun exportCsv(writeBytes: suspend (ByteArray) -> Unit) {
        if (_status.value == Status.Working) return
        _status.value = Status.Working
        viewModelScope.launch {
            _status.value = try {
                val bytes = withContext(Dispatchers.IO) {
                    val data = repository.exportHistoryCsv().toByteArray(Charsets.UTF_8)
                    writeBytes(data)
                    data.size
                }
                Status.CsvDone(bytes)
            } catch (e: Exception) {
                Status.Error("Couldn't write the CSV file.")
            }
        }
    }

    /** [readText] returns the full text of the file the user picked. */
    fun restore(readText: suspend () -> String) {
        if (_status.value == Status.Working) return
        _status.value = Status.Working
        viewModelScope.launch {
            _status.value = try {
                val result = withContext(Dispatchers.IO) {
                    repository.restore(readText())
                }
                Status.RestoreDone(result)
            } catch (e: BackupFormatException) {
                Status.Error(e.message ?: "This isn't a valid Dhikr backup file.")
            } catch (e: Exception) {
                Status.Error("Restore failed. Your data hasn't changed.")
            }
        }
    }

    class Factory(
        private val repository: BackupRepository,
        private val appVersionName: String,
        private val lastBackupAtFlow: Flow<Long> = flowOf(0L),
        private val onBackedUp: suspend () -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BackupViewModel(repository, appVersionName, lastBackupAtFlow, onBackedUp) as T
    }
}
