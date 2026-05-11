package com.nearlink.messenger.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.prefsDataStore by preferencesDataStore("nearlink_settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val store = ctx.prefsDataStore

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val NICKNAME = stringPreferencesKey("nickname")
        val AVATAR_URI = stringPreferencesKey("avatar_uri")
        val LAST_SYNCED_TS = longPreferencesKey("last_synced_ts")
        val DB_ENCRYPTION = booleanPreferencesKey("db_encryption_enabled")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val PERMISSION_PROMPT_SEEN = booleanPreferencesKey("permission_prompt_seen")
    }

    val serverUrl: Flow<String?> = store.data.map { it[Keys.SERVER_URL] }
    val nickname: Flow<String?> = store.data.map { it[Keys.NICKNAME] }
    val avatarUri: Flow<String?> = store.data.map { it[Keys.AVATAR_URI] }
    val lastSyncedTs: Flow<Long> = store.data.map { it[Keys.LAST_SYNCED_TS] ?: 0L }
    val dbEncryptionEnabled: Flow<Boolean> = store.data.map { it[Keys.DB_ENCRYPTION] ?: false }
    val onboarded: Flow<Boolean> = store.data.map { it[Keys.ONBOARDED] ?: false }
    val permissionPromptSeen: Flow<Boolean> = store.data.map { it[Keys.PERMISSION_PROMPT_SEEN] ?: false }

    suspend fun setServerUrl(url: String?) = store.edit {
        if (url.isNullOrBlank()) it.remove(Keys.SERVER_URL) else it[Keys.SERVER_URL] = url
    }
    suspend fun setNickname(name: String) = store.edit { it[Keys.NICKNAME] = name }
    suspend fun setAvatarUri(uri: String?) = store.edit {
        if (uri == null) it.remove(Keys.AVATAR_URI) else it[Keys.AVATAR_URI] = uri
    }
    suspend fun setLastSyncedTs(ts: Long) = store.edit { it[Keys.LAST_SYNCED_TS] = ts }
    suspend fun setDbEncryption(enabled: Boolean) = store.edit { it[Keys.DB_ENCRYPTION] = enabled }
    suspend fun setOnboarded(v: Boolean) = store.edit { it[Keys.ONBOARDED] = v }
    suspend fun setPermissionPromptSeen(v: Boolean) = store.edit { it[Keys.PERMISSION_PROMPT_SEEN] = v }
}
