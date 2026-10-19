package com.dhikr.app.feature.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.sync.CloudProvider
import com.dhikr.app.core.sync.ProviderId
import com.dhikr.app.core.sync.SyncAuth
import com.dhikr.app.core.sync.SyncEngine
import com.dhikr.app.core.sync.SyncException
import com.dhikr.app.core.sync.SyncStateStorage
import com.dhikr.app.core.sync.UndoResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SyncMessage {
    data class Success(val localChanges: Int, val remoteChanges: Int) : SyncMessage
    data class Error(val kind: SyncException.Kind) : SyncMessage
    data object Undone : SyncMessage
    data object NoUndo : SyncMessage
    data object UndoBlocked : SyncMessage
}

data class SyncUi(
    val provider: ProviderId? = null,
    val accountLabel: String? = null,
    val lastSyncedAt: Long = 0L,
    val running: Boolean = false,
    val message: SyncMessage? = null,
    val canUndo: Boolean = false,
    val googleConfigured: Boolean = false,
    val microsoftConfigured: Boolean = false,
)

/** Drives the Cloud sync section of Settings. Holds no Context; the screen launches the
 *  sign-in Intent and hands the result back. */
class SyncViewModel(
    private val engine: SyncEngine,
    private val state: SyncStateStorage,
    private val auth: SyncAuth,
    private val providerFactory: (ProviderId) -> CloudProvider,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _ui = MutableStateFlow(snapshot())
    val ui: StateFlow<SyncUi> = _ui.asStateFlow()

    private fun snapshot() = SyncUi(
        provider = state.provider,
        accountLabel = state.accountLabel,
        lastSyncedAt = state.lastSyncedAt,
        canUndo = engine.hasSnapshot(),
        googleConfigured = auth.isConfigured(ProviderId.GOOGLE),
        microsoftConfigured = auth.isConfigured(ProviderId.MICROSOFT),
    )

    /** Intent for the browser sign-in, or null if the provider isn't set up. */
    fun connect(provider: ProviderId): Intent? =
        if (auth.isConfigured(provider)) auth.authorizationIntent(provider) else null

    /** [data] is the redirect result; null means the user backed out. */
    fun onSignInResult(provider: ProviderId, data: Intent?) {
        if (data == null) return
        viewModelScope.launch {
            try {
                val label = auth.completeSignIn(provider, data)
                val previous = state.provider
                if (previous != null && previous != provider) {
                    // Different cloud = different remote history; start fresh and drop the old login.
                    auth.signOut(previous)
                    engine.resetBase()
                }
                state.provider = provider
                state.accountLabel = label
                state.lastSyncedAt = if (previous == provider) state.lastSyncedAt else 0L
                _ui.value = snapshot().copy(message = null)
            } catch (e: SyncException) {
                _ui.update { it.copy(message = SyncMessage.Error(e.kind)) }
            }
        }
    }

    fun disconnect() {
        state.provider?.let(auth::signOut)
        state.provider = null
        state.accountLabel = null
        state.lastSyncedAt = 0L
        engine.resetBase()
        _ui.value = snapshot().copy(message = null)
    }

    fun syncNow() {
        val provider = state.provider ?: return
        if (_ui.value.running) return
        _ui.update { it.copy(running = true, message = null) }
        viewModelScope.launch {
            val message = try {
                val summary = engine.sync(providerFactory(provider))
                state.lastSyncedAt = clock()
                SyncMessage.Success(summary.localChanges, summary.remoteChanges)
            } catch (e: SyncException) {
                SyncMessage.Error(e.kind)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SyncMessage.Error(SyncException.Kind.UNKNOWN)
            }
            _ui.value = snapshot().copy(message = message)
        }
    }

    fun undo() {
        if (_ui.value.running) return
        _ui.update { it.copy(running = true, message = null) }
        viewModelScope.launch {
            val message = try {
                when (engine.undoLast()) {
                    UndoResult.DONE -> SyncMessage.Undone
                    UndoResult.NOTHING_TO_UNDO -> SyncMessage.NoUndo
                    UndoResult.CHANGED_SINCE -> SyncMessage.UndoBlocked
                }
            } catch (e: SyncException) {
                SyncMessage.Error(e.kind)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SyncMessage.Error(SyncException.Kind.UNKNOWN)
            }
            _ui.value = snapshot().copy(message = message)
        }
    }

    class Factory(
        private val engine: SyncEngine,
        private val state: SyncStateStorage,
        private val auth: SyncAuth,
        private val providerFactory: (ProviderId) -> CloudProvider,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SyncViewModel(engine, state, auth, providerFactory) as T
    }
}
