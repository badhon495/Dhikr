package com.dhikr.app.core.sync

import android.content.Context

enum class ProviderId { GOOGLE, MICROSOFT }

/**
 * Non-secret sync bookkeeping (which provider, who is signed in, when we last
 * synced) in plain SharedPreferences. Tokens live in [SyncTokenStore].
 */
class SyncStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var provider: ProviderId?
        get() = prefs.getString(KEY_PROVIDER, null)?.let { name -> ProviderId.values().firstOrNull { it.name == name } }
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_PROVIDER) else putString(KEY_PROVIDER, value.name) }.apply()

    var accountLabel: String?
        get() = prefs.getString(KEY_ACCOUNT, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_ACCOUNT) else putString(KEY_ACCOUNT, value) }.apply()

    /** Epoch millis of the last successful sync; 0 = never. */
    var lastSyncedAt: Long
        get() = prefs.getLong(KEY_LAST_SYNCED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNCED, value).apply()

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val NAME = "sync_state"
        const val KEY_PROVIDER = "provider"
        const val KEY_ACCOUNT = "account_label"
        const val KEY_LAST_SYNCED = "last_synced_at"
    }
}
