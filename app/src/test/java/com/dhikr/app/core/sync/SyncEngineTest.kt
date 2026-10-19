package com.dhikr.app.core.sync

import com.dhikr.app.core.backup.BackupFile
import com.dhikr.app.core.backup.BackupPreferences
import com.dhikr.app.core.backup.BackupTasbih
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

    private fun t(id: String, name: String = id, updatedAt: Long = 1L) = BackupTasbih(
        id = id, name = name, arabic = "", translation = "",
        lapTarget = 33, lapCount = 3, createdAt = 0L, updatedAt = updatedAt,
    )

    private fun file(vararg tasbih: BackupTasbih) = BackupFile(
        customTasbih = tasbih.toList(), preferences = BackupPreferences(),
    )

    private class FakeLocal(var state: BackupFile) : SyncLocal {
        var applyCalls = 0
        override suspend fun readState() = state
        override suspend fun applyState(target: BackupFile) {
            applyCalls++
            state = target
        }
    }

    private class FakeFiles : SyncFiles {
        var base: String? = null
        var snapshot: String? = null
        override fun readBase() = base
        override fun writeBase(text: String) { base = text }
        override fun deleteBase() { base = null }
        override fun readSnapshot() = snapshot
        override fun writeSnapshot(text: String) { snapshot = text }
    }

    private class FakeProvider(var content: String? = null) : CloudProvider {
        var etag = 0
        var uploads = 0
        var failUpload: SyncException? = null
        var conflictsRemaining = 0
        var downloadGate: CompletableDeferred<Unit>? = null

        override suspend fun download(): RemoteFile? {
            downloadGate?.await()
            return content?.let { RemoteFile(it, etag.toString()) }
        }

        override suspend fun upload(content: String, ifMatch: String?): String {
            failUpload?.let { throw it }
            if (conflictsRemaining > 0) {
                conflictsRemaining--
                throw SyncException(SyncException.Kind.CONFLICT, "etag mismatch")
            }
            uploads++
            this.content = content
            etag++
            return etag.toString()
        }
    }

    private fun kindOf(block: suspend () -> Unit): SyncException.Kind {
        var kind: SyncException.Kind? = null
        kotlinx.coroutines.runBlocking {
            try {
                block()
            } catch (e: SyncException) {
                kind = e.kind
            }
        }
        return kind ?: run { fail("expected SyncException"); throw AssertionError() }
    }

    @Test
    fun firstSyncUploadsLocalAndWritesBase() = kotlinx.coroutines.runBlocking {
        val local = FakeLocal(file(t("a")))
        val files = FakeFiles()
        val provider = FakeProvider()
        SyncEngine(local, files).sync(provider)
        assertEquals(listOf("a"), SyncCodec.decode(provider.content!!).customTasbih.map { it.id })
        assertNotNull(files.base)
    }

    @Test
    fun secondSyncPullsRemoteEdit() = kotlinx.coroutines.runBlocking {
        val local = FakeLocal(file(t("a", "old", 1)))
        val files = FakeFiles()
        val provider = FakeProvider()
        val engine = SyncEngine(local, files)
        engine.sync(provider)
        provider.content = SyncCodec.encode(file(t("a", "new", 5)))
        val summary = engine.sync(provider)
        assertEquals("new", local.state.customTasbih.single().name)
        assertEquals(1, summary.localChanges)
    }

    @Test
    fun uploadFailureDoesNotWriteBase() {
        val local = FakeLocal(file(t("a")))
        val files = FakeFiles()
        val provider = FakeProvider().apply {
            failUpload = SyncException(SyncException.Kind.NETWORK, "offline")
        }
        val kind = kindOf { SyncEngine(local, files).sync(provider) }
        assertEquals(SyncException.Kind.NETWORK, kind)
        assertNull(files.base)
    }

    @Test
    fun conflictRetriesOnceThenSucceeds() = kotlinx.coroutines.runBlocking {
        val files = FakeFiles()
        val provider = FakeProvider().apply { conflictsRemaining = 1 }
        SyncEngine(FakeLocal(file(t("a"))), files).sync(provider)
        assertEquals(1, provider.uploads)
        assertNotNull(files.base)
    }

    @Test
    fun secondConflictThrows() {
        val files = FakeFiles()
        val provider = FakeProvider().apply { conflictsRemaining = 2 }
        val kind = kindOf { SyncEngine(FakeLocal(file(t("a"))), files).sync(provider) }
        assertEquals(SyncException.Kind.CONFLICT, kind)
        assertNull(files.base)
    }

    @Test
    fun corruptRemoteLeavesLocalAndBaseUntouched() {
        val local = FakeLocal(file(t("a")))
        val files = FakeFiles()
        val provider = FakeProvider("garbage")
        val kind = kindOf { SyncEngine(local, files).sync(provider) }
        assertEquals(SyncException.Kind.CORRUPT, kind)
        assertEquals(0, local.applyCalls)
        assertNull(files.base)
    }

    @Test
    fun newerFormatRemoteRejected() {
        val local = FakeLocal(file(t("a")))
        val provider = FakeProvider("""{"format":"dhikr.sync","version":99}""")
        val kind = kindOf { SyncEngine(local, FakeFiles()).sync(provider) }
        assertEquals(SyncException.Kind.NEWER_FORMAT, kind)
        assertEquals(0, local.applyCalls)
    }

    @Test
    fun concurrentSyncRejectedWithBusy() = runTest {
        val gate = CompletableDeferred<Unit>()
        val provider = FakeProvider().apply { downloadGate = gate }
        val engine = SyncEngine(FakeLocal(file(t("a"))), FakeFiles())
        val first = async { engine.sync(provider) }
        runCurrent()
        try {
            engine.sync(provider)
            fail("expected BUSY")
        } catch (e: SyncException) {
            assertEquals(SyncException.Kind.BUSY, e.kind)
        }
        gate.complete(Unit)
        first.await()
    }

    @Test
    fun undoRestoresSnapshotAndClearsBase() = kotlinx.coroutines.runBlocking {
        val local = FakeLocal(file(t("a")))
        val files = FakeFiles()
        val provider = FakeProvider(SyncCodec.encode(file(t("b"))))
        val engine = SyncEngine(local, files)
        engine.sync(provider)
        assertEquals(listOf("a", "b"), local.state.customTasbih.map { it.id })
        assertTrue(engine.undoLast())
        assertEquals(listOf("a"), local.state.customTasbih.map { it.id })
        assertNull(files.base)
    }

    @Test
    fun undoWithoutSnapshotReturnsFalse() = kotlinx.coroutines.runBlocking {
        assertFalse(SyncEngine(FakeLocal(file()), FakeFiles()).undoLast())
    }
}
