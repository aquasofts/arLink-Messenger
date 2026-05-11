package com.nearlink.messenger.data.repository

import com.nearlink.messenger.core.network.WebSocketEngine
import com.nearlink.messenger.data.local.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val store: SettingsStore,
    private val ws: WebSocketEngine,
) {
    val serverUrl: Flow<String?> get() = store.serverUrl
    val dbEncryptionEnabled: Flow<Boolean> get() = store.dbEncryptionEnabled
    val lastSyncedTs: Flow<Long> get() = store.lastSyncedTs
    val permissionPromptSeen: Flow<Boolean> get() = store.permissionPromptSeen

    suspend fun setServerUrl(url: String?) {
        store.setServerUrl(url)
        if (url.isNullOrBlank()) ws.disconnect() else ws.connect(url)
    }

    suspend fun connectIfConfigured() {
        val url = store.serverUrl.first()
        if (!url.isNullOrBlank()) ws.connect(url)
    }

    suspend fun setLastSyncedTs(ts: Long) = store.setLastSyncedTs(ts)
    suspend fun setDbEncryption(enabled: Boolean) = store.setDbEncryption(enabled)
    suspend fun setPermissionPromptSeen(v: Boolean) = store.setPermissionPromptSeen(v)
}
