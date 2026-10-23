package com.dhikr.app.core.drivesync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Production DataStore for Drive sync metadata; wire via [DriveFileIdStore]. */
val Context.driveSyncDataStore by preferencesDataStore(name = "drive_sync")

/**
 * Caches the Drive file id of this app's backup file so sync doesn't have to
 * search Drive on every run. Backed by [DataStore] so the id survives process death.
 */
class DriveFileIdStore(private val dataStore: DataStore<Preferences>) {
    private val fileIdKey = stringPreferencesKey("drive_file_id")

    suspend fun get(): String? = dataStore.data.first()[fileIdKey]

    suspend fun set(fileId: String) {
        dataStore.edit { it[fileIdKey] = fileId }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(fileIdKey) }
    }
}
