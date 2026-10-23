package com.dhikr.app.core.drivesync

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.dhikr.app.core.backup.BackupFormatException
import com.dhikr.app.core.backup.RestoreResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class FakeTokenProvider(private val result: Result<String>) : DriveTokenProvider {
    override suspend fun getValidAccessToken(activityContext: Context) = result
}

/**
 * Drives [DriveApi]'s real implementation through its injectable `httpClient`
 * lambda (the same seam [DriveApiTest] uses) so these tests exercise the real
 * request/response parsing without touching the network, while still letting
 * each test configure per-call results and observe whether `findFileId` ran.
 */
private class FakeDriveApi(
    findFileIdResult: String? = null,
    uploadOrUpdateResult: String = "newid",
    downloadContentResult: String = "{}",
    private val failWith: DriveApi.DriveApiException? = null,
) {
    var findFileIdCalled = false
        private set

    val delegate = DriveApi(httpClient = { request ->
        failWith?.let { throw it }
        when {
            request.method == "GET" && request.url.contains("/files?q=") -> {
                findFileIdCalled = true
                if (findFileIdResult != null) {
                    DriveApi.Response(200, """{"files":[{"id":"$findFileIdResult"}]}""")
                } else {
                    DriveApi.Response(200, """{"files":[]}""")
                }
            }
            request.method == "POST" -> DriveApi.Response(200, """{"id":"$uploadOrUpdateResult"}""")
            request.method == "PATCH" -> DriveApi.Response(200, "{}")
            request.method == "GET" -> DriveApi.Response(200, downloadContentResult)
            else -> error("unexpected request: $request")
        }
    })
}

class DriveBackupRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun newFileIdStore(): DriveFileIdStore {
        val testScope = TestScope(UnconfinedTestDispatcher())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFolder.newFile("test_${System.nanoTime()}.preferences_pb") },
        )
        return DriveFileIdStore(dataStore)
    }

    // DriveBackupRepository only ever forwards this to
    // DriveTokenProvider.getValidAccessToken, and FakeTokenProvider ignores
    // its argument entirely — a base-less ContextWrapper satisfies the
    // non-null Context parameter without needing Robolectric/Mockito.
    private val context: Context = ContextWrapper(null)

    private val fakeExportJson: suspend () -> String = { "{}" }

    private val fakeRestoreJson: suspend (String) -> RestoreResult = {
        RestoreResult(
            tasbihRestored = 0,
            routinesRestored = 0,
            sessionsRestored = 0,
            sessionsSkipped = 0,
            preferencesApplied = true,
        )
    }

    private val throwingRestoreJson: suspend (String) -> RestoreResult = {
        throw BackupFormatException("bad format")
    }

    @Test
    fun `backup with no cached file id searches by name then uploads as new`() = runTest {
        val fileIdStore = newFileIdStore()
        val api = FakeDriveApi(findFileIdResult = null, uploadOrUpdateResult = "newid")
        val repo = DriveBackupRepository(
            FakeTokenProvider(Result.success("tok")),
            api.delegate,
            fileIdStore,
            fakeExportJson,
            fakeRestoreJson,
        )
        val outcome = repo.backup(context)
        assertTrue(outcome is DriveBackupRepository.BackupOutcome.Success)
        assertEquals("newid", fileIdStore.get())
    }

    @Test
    fun `backup with cached file id updates existing file directly, skips search`() = runTest {
        val fileIdStore = newFileIdStore().also { it.set("existing") }
        val api = FakeDriveApi(findFileIdResult = null, uploadOrUpdateResult = "existing")
        val repo = DriveBackupRepository(
            FakeTokenProvider(Result.success("tok")),
            api.delegate,
            fileIdStore,
            fakeExportJson,
            fakeRestoreJson,
        )
        repo.backup(context)
        assertFalse(api.findFileIdCalled)
    }

    @Test
    fun `backup returns AuthFailed when token provider fails`() = runTest {
        val repo = DriveBackupRepository(
            FakeTokenProvider(Result.failure(Exception("no auth"))),
            FakeDriveApi().delegate,
            newFileIdStore(),
            fakeExportJson,
            fakeRestoreJson,
        )
        val outcome = repo.backup(context)
        assertTrue(outcome is DriveBackupRepository.BackupOutcome.AuthFailed)
    }

    @Test
    fun `backup returns AuthFailed when upload fails with 401`() = runTest {
        val api = FakeDriveApi(
            findFileIdResult = null,
            failWith = DriveApi.DriveApiException(401, "unauthorized"),
        )
        val repo = DriveBackupRepository(
            FakeTokenProvider(Result.success("tok")),
            api.delegate,
            newFileIdStore(),
            fakeExportJson,
            fakeRestoreJson,
        )
        val outcome = repo.backup(context)
        assertTrue(outcome is DriveBackupRepository.BackupOutcome.AuthFailed)
    }

    @Test
    fun `backup returns NetworkFailed when upload fails with 500`() = runTest {
        val api = FakeDriveApi(
            findFileIdResult = null,
            failWith = DriveApi.DriveApiException(500, "server error"),
        )
        val repo = DriveBackupRepository(
            FakeTokenProvider(Result.success("tok")),
            api.delegate,
            newFileIdStore(),
            fakeExportJson,
            fakeRestoreJson,
        )
        val outcome = repo.backup(context)
        assertTrue(outcome is DriveBackupRepository.BackupOutcome.NetworkFailed)
    }

    @Test
    fun `restore returns NoBackupFound when no cached id and search finds nothing`() = runTest {
        val api = FakeDriveApi(findFileIdResult = null)
        val repo = DriveBackupRepository(
            FakeTokenProvider(Result.success("tok")),
            api.delegate,
            newFileIdStore(),
            fakeExportJson,
            fakeRestoreJson,
        )
        val outcome = repo.restore(context)
        assertEquals(DriveBackupRepository.RestoreOutcome.NoBackupFound, outcome)
    }

    @Test
    fun `restore returns AuthFailed when token provider fails`() = runTest {
        val repo = DriveBackupRepository(
            FakeTokenProvider(Result.failure(Exception("no auth"))),
            FakeDriveApi().delegate,
            newFileIdStore(),
            fakeExportJson,
            fakeRestoreJson,
        )
        val outcome = repo.restore(context)
        assertTrue(outcome is DriveBackupRepository.RestoreOutcome.AuthFailed)
    }

    @Test
    fun `restore returns FormatInvalid when downloaded content isn't a valid backup`() = runTest {
        val fileIdStore = newFileIdStore().also { it.set("id1") }
        val api = FakeDriveApi(downloadContentResult = "not json")
        val repo = DriveBackupRepository(
            FakeTokenProvider(Result.success("tok")),
            api.delegate,
            fileIdStore,
            fakeExportJson,
            throwingRestoreJson,
        )
        val outcome = repo.restore(context)
        assertTrue(outcome is DriveBackupRepository.RestoreOutcome.FormatInvalid)
    }

    @Test
    fun `restore returns Success and applies the downloaded backup`() = runTest {
        val fileIdStore = newFileIdStore().also { it.set("id1") }
        val api = FakeDriveApi(downloadContentResult = """{"valid":true}""")
        val repo = DriveBackupRepository(
            FakeTokenProvider(Result.success("tok")),
            api.delegate,
            fileIdStore,
            fakeExportJson,
            fakeRestoreJson,
        )
        val outcome = repo.restore(context)
        assertTrue(outcome is DriveBackupRepository.RestoreOutcome.Success)
    }

    @Test
    fun `restore resolves file id via search when no cached id, then downloads`() = runTest {
        val fileIdStore = newFileIdStore()
        val api = FakeDriveApi(findFileIdResult = "found-id", downloadContentResult = "{}")
        val repo = DriveBackupRepository(
            FakeTokenProvider(Result.success("tok")),
            api.delegate,
            fileIdStore,
            fakeExportJson,
            fakeRestoreJson,
        )
        val outcome = repo.restore(context)
        assertTrue(outcome is DriveBackupRepository.RestoreOutcome.Success)
        assertTrue(api.findFileIdCalled)
        assertEquals("found-id", fileIdStore.get())
    }

    @Test
    fun `restore returns NetworkFailed when download fails with non-auth error`() = runTest {
        val fileIdStore = newFileIdStore().also { it.set("id1") }
        val api = FakeDriveApi(failWith = DriveApi.DriveApiException(500, "server error"))
        val repo = DriveBackupRepository(
            FakeTokenProvider(Result.success("tok")),
            api.delegate,
            fileIdStore,
            fakeExportJson,
            fakeRestoreJson,
        )
        val outcome = repo.restore(context)
        assertTrue(outcome is DriveBackupRepository.RestoreOutcome.NetworkFailed)
    }
}
