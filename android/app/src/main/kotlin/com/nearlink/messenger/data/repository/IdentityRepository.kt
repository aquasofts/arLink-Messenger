package com.nearlink.messenger.data.repository

import com.nearlink.messenger.core.crypto.IdentityKeyStore
import com.nearlink.messenger.core.crypto.PublicIdentity
import com.nearlink.messenger.data.local.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdentityRepository @Inject constructor(
    private val keyStore: IdentityKeyStore,
    private val settings: SettingsStore,
) {
    suspend fun bootstrap(): PublicIdentity = keyStore.initializeIfAbsent()
    suspend fun publicIdentity(): PublicIdentity = keyStore.loadPublic()

    val nickname: Flow<String?> get() = settings.nickname
    suspend fun setNickname(name: String) = settings.setNickname(name)
    val avatarUri: Flow<String?> get() = settings.avatarUri
    suspend fun setAvatarUri(uri: String?) = settings.setAvatarUri(uri)

    suspend fun isOnboarded(): Boolean = settings.onboarded.first()
    suspend fun markOnboarded() = settings.setOnboarded(true)
}
