package com.dhikr.app.core.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dhikr.app.core.ai.openResettingOnCorruption
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Encrypted OAuth tokens for the connected cloud account, one slot per provider.
 * Same recovery behaviour as the Gemini key store: an undecryptable file is wiped
 * and the user simply reconnects.
 */
class SyncTokenStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        openResettingOnCorruption(
            reset = {
                appContext.deleteSharedPreferences(NAME)
                java.io.File("${appContext.applicationInfo.dataDir}/shared_prefs/$NAME.xml").delete()
            },
            open = {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext, NAME, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            },
        )
    }

    fun save(provider: ProviderId, refreshToken: String?, accessToken: String, expiresAtMillis: Long) {
        prefs.edit().apply {
            // A refresh response may omit a new refresh token; keep the old one then.
            if (refreshToken != null) putString(key(provider, "refresh"), refreshToken)
            putString(key(provider, "access"), accessToken)
            putLong(key(provider, "expires"), expiresAtMillis)
        }.apply()
    }

    fun refreshToken(provider: ProviderId): String? = safe { prefs.getString(key(provider, "refresh"), null) }

    /** Access token and its expiry (epoch millis), or null if none stored. */
    fun accessToken(provider: ProviderId): Pair<String, Long>? = safe {
        val token = prefs.getString(key(provider, "access"), null) ?: return@safe null
        token to prefs.getLong(key(provider, "expires"), 0L)
    }

    fun clear(provider: ProviderId) {
        prefs.edit()
            .remove(key(provider, "refresh"))
            .remove(key(provider, "access"))
            .remove(key(provider, "expires"))
            .apply()
    }

    private inline fun <T> safe(block: () -> T?): T? =
        try {
            block()
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IOException) {
            null
        }

    private fun key(provider: ProviderId, field: String) = "${provider.name.lowercase()}_$field"

    private companion object {
        const val NAME = "sync_secrets"
    }
}
