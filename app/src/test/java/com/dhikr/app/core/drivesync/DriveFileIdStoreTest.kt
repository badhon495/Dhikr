package com.dhikr.app.core.drivesync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DriveFileIdStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun newDataStore(): DataStore<Preferences> {
        val testScope = TestScope(UnconfinedTestDispatcher())
        return PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
    }

    @Test
    fun `get returns null before any set`() = runTest {
        val store = DriveFileIdStore(newDataStore())
        assertNull(store.get())
    }

    @Test
    fun `set then get returns the stored id`() = runTest {
        val store = DriveFileIdStore(newDataStore())
        store.set("abc123")
        assertEquals("abc123", store.get())
    }

    @Test
    fun `clear removes the stored id`() = runTest {
        val store = DriveFileIdStore(newDataStore())
        store.set("abc123")
        store.clear()
        assertNull(store.get())
    }
}
