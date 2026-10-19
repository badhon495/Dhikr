package com.dhikr.app.feature.settings

import android.content.Intent
import com.dhikr.app.core.backup.BackupFile
import com.dhikr.app.core.backup.BackupTasbih
import com.dhikr.app.core.sync.CloudProvider
import com.dhikr.app.core.sync.ProviderId
import com.dhikr.app.core.sync.RemoteFile
import com.dhikr.app.core.sync.SyncAuth
import com.dhikr.app.core.sync.SyncCodec
import com.dhikr.app.core.sync.SyncEngine
import com.dhikr.app.core.sync.SyncException
import com.dhikr.app.core.sync.SyncFiles
import com.dhikr.app.core.sync.SyncLocal
import com.dhikr.app.core.sync.SyncStateStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {

    private class FakeLocal : SyncLocal {
        var state = BackupFile(
            customTasbih = listOf(
                BackupTasbih(
                    id = "a", name = "a", arabic = "", translation = "",
                    lapTarget = 33, lapCount = 3, createdAt = 0, updatedAt = 1,
                ),
            ),
        )
        override suspend fun readState() = state
        override suspend fun applyState(target: BackupFile) { state = target }
    }

    private class FakeFiles : SyncFiles {
        var base: String? = null
        var snapshot: String? = null
        override fun readBase() = base
        override fun writeBase(text: String) { base = text }
        override fun deleteBase() { base = null }
        override fun readSnapshot() = snapshot
        override fun writeSnapshot(text: String) { snapshot = text }
        override fun deleteSnapshot() { snapshot = null }
    }

    private class FakeProvider : CloudProvider {
        var content: String? = null
        var failure: SyncException? = null
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun download(): RemoteFile? {
            gate?.await()
            failure?.let { throw it }
            return content?.let { RemoteFile(it, "1") }
        }
        override suspend fun upload(content: String, ifMatch: String?): String {
            this.content = content
            return "2"
        }
    }

    private class FakeAuth(val configured: Set<ProviderId>) : SyncAuth {
        val signedOut = mutableListOf<ProviderId>()
        override fun isConfigured(provider: ProviderId) = provider in configured
        override fun authorizationIntent(provider: ProviderId): Intent? = Intent()
        override suspend fun completeSignIn(provider: ProviderId, data: Intent) = "me@example.com"
        override fun signOut(provider: ProviderId) { signedOut += provider }
    }

    private class FakeState : SyncStateStorage {
        override var provider: ProviderId? = null
        override var accountLabel: String? = null
        override var lastSyncedAt: Long = 0L
    }

    private val files = FakeFiles()
    private val provider = FakeProvider()
    private val state = FakeState()
    private val auth = FakeAuth(setOf(ProviderId.GOOGLE, ProviderId.MICROSOFT))

    private fun vm(a: SyncAuth = auth) = SyncViewModel(
        engine = SyncEngine(FakeLocal(), files),
        state = state,
        auth = a,
        providerFactory = { provider },
        clock = { 1234L },
    )

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun connected(): SyncViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val vm = vm()
        vm.onSignInResult(ProviderId.GOOGLE, Intent())
        return vm
    }

    @Test
    fun syncNowSuccessSetsMessageAndLastSynced() {
        val vm = connected()
        vm.syncNow()
        assertEquals(1234L, vm.ui.value.lastSyncedAt)
        assertTrue(vm.ui.value.message is SyncMessage.Success)
        assertTrue(!vm.ui.value.running)
    }

    @Test
    fun errorKindIsSurfaced() {
        val vm = connected()
        provider.failure = SyncException(SyncException.Kind.NETWORK, "offline")
        vm.syncNow()
        assertEquals(SyncMessage.Error(SyncException.Kind.NETWORK), vm.ui.value.message)
        assertEquals(0L, vm.ui.value.lastSyncedAt)
    }

    @Test
    fun syncNowIgnoredWhileRunning() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = vm()
        vm.onSignInResult(ProviderId.GOOGLE, Intent())
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        provider.gate = gate
        vm.syncNow()
        runCurrent()
        assertTrue(vm.ui.value.running)
        vm.syncNow() // must be ignored, not surface BUSY
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertTrue(vm.ui.value.message is SyncMessage.Success)
    }

    @Test
    fun switchingProviderResetsBase() {
        val vm = connected()
        vm.syncNow()
        assertTrue(files.base != null)
        vm.onSignInResult(ProviderId.MICROSOFT, Intent())
        assertNull(files.base)
        assertEquals(ProviderId.MICROSOFT, state.provider)
        assertEquals(listOf(ProviderId.GOOGLE), auth.signedOut)
    }

    @Test
    fun disconnectClearsTokensStateAndBase() {
        val vm = connected()
        vm.syncNow()
        vm.disconnect()
        assertNull(state.provider)
        assertNull(files.base)
        assertEquals(0L, state.lastSyncedAt)
        assertEquals(listOf(ProviderId.GOOGLE), auth.signedOut)
    }

    @Test
    fun unconfiguredProviderCannotConnect() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val vm = vm(FakeAuth(emptySet()))
        assertNull(vm.connect(ProviderId.GOOGLE))
        assertTrue(!vm.ui.value.googleConfigured)
    }

    @Test
    fun undoWithoutSnapshotShowsNoUndo() {
        val vm = connected()
        vm.undo()
        assertEquals(SyncMessage.NoUndo, vm.ui.value.message)
    }

    @Test
    fun undoAfterSyncShowsUndone() {
        val vm = connected()
        provider.content = SyncCodec.encode(BackupFile())
        vm.syncNow()
        assertTrue(vm.ui.value.canUndo)
        vm.undo()
        assertEquals(SyncMessage.Undone, vm.ui.value.message)
    }
}
