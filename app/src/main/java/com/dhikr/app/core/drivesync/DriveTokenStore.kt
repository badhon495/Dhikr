package com.dhikr.app.core.drivesync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dhikr.app.core.ai.openResettingOnCorruption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Encrypted at-rest storage for the cached Google Drive OAuth access token.
 * Backed by [EncryptedSharedPreferences]; the token never leaves the device
 * except in requests to Google Drive.
 *
 * If the backing file can't be decrypted — most commonly after Android
 * auto-backup restores it onto a new device whose hardware keystore never
 * received the master key — opening it once wipes the unreadable file and
 * starts fresh (see [openResettingOnCorruption]). The stored token is lost in
 * that case and the user re-authorizes; the alternative is a crash on every
 * screen that reads the store.
 */
class DriveTokenStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        openResettingOnCorruption(
            reset = { deletePrefsFile(PREFS_NAME) },
            open = { createEncryptedPrefs() },
        )
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Deletes the on-disk prefs file (data + Tink keysets) so the next
     *  [createEncryptedPrefs] rebuilds it under the current master key. */
    private fun deletePrefsFile(name: String) {
        appContext.deleteSharedPreferences(name)
        // deleteSharedPreferences only removes an already-loaded instance's
        // file lazily on some OS versions; remove the backing file directly too.
        val dir = appContext.applicationInfo.dataDir
        java.io.File("$dir/shared_prefs/$name.xml").delete()
    }

    fun getAccessToken(): String? =
        try {
            prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
        } catch (e: GeneralSecurityException) {
            // A single entry failed to decrypt even though the file opened.
            // Treat as "no token" rather than crashing the caller.
            null
        } catch (e: IOException) {
            null
        }

    fun getExpiryMillis(): Long? =
        try {
            val value = prefs.getLong(KEY_EXPIRY_MILLIS, -1L)
            if (value == -1L) null else value
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IOException) {
            null
        }

    val isExpired: Boolean get() {
        val expiryMillis = getExpiryMillis()
        return expiryMillis == null || System.currentTimeMillis() >= expiryMillis
    }

    suspend fun setToken(accessToken: String, expiryMillis: Long) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putLong(KEY_EXPIRY_MILLIS, expiryMillis)
        }.apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_EXPIRY_MILLIS)
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "drive_sync_secrets"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_EXPIRY_MILLIS = "expiry_millis"
    }
}
