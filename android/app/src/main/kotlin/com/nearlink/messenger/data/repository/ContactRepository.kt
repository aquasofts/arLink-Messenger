package com.nearlink.messenger.data.repository

import com.nearlink.messenger.core.model.Contact
import com.nearlink.messenger.core.model.TrustState
import com.nearlink.messenger.data.local.dao.ContactDao
import com.nearlink.messenger.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val dao: ContactDao,
) {

    fun observeAll(): Flow<List<Contact>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(deviceId: String): Contact? = dao.getById(deviceId)?.toDomain()

    fun observe(deviceId: String): Flow<Contact?> = dao.observeById(deviceId).map { it?.toDomain() }

    suspend fun upsert(contact: Contact) = dao.upsert(contact.toEntity())

    suspend fun setTrustState(deviceId: String, state: TrustState, now: Long = System.currentTimeMillis()) =
        dao.setTrustState(deviceId, state.name, now)

    suspend fun setLastSeen(deviceId: String, ts: Long) = dao.setLastSeen(deviceId, ts)

    suspend fun setBlocked(deviceId: String, blocked: Boolean) = dao.setBlocked(deviceId, blocked)
}

internal fun ContactEntity.toDomain(): Contact = Contact(
    deviceId = deviceId,
    nickname = nickname,
    avatarUri = avatarUri,
    pkIdentity = pkIdentity,
    pkX = pkX,
    trustState = trustState,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
    lastSeenMs = lastSeenMs,
)

internal fun Contact.toEntity(): ContactEntity = ContactEntity(
    deviceId = deviceId,
    nickname = nickname,
    avatarUri = avatarUri,
    pkIdentity = pkIdentity,
    pkX = pkX,
    trustState = trustState,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
    lastSeenMs = lastSeenMs,
)
