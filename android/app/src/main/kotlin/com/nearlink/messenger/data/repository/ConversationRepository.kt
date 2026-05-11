package com.nearlink.messenger.data.repository

import com.nearlink.messenger.core.model.Conversation
import com.nearlink.messenger.data.local.dao.ConversationDao
import com.nearlink.messenger.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val dao: ConversationDao,
) {

    fun observeAll(): Flow<List<Conversation>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observe(convId: String): Flow<Conversation?> =
        dao.observeById(convId).map { it?.toDomain() }

    suspend fun get(convId: String): Conversation? = dao.getById(convId)?.toDomain()

    suspend fun ensureForPeer(peerDeviceId: String, title: String): Conversation {
        dao.getByPeer(peerDeviceId)?.let { return it.toDomain() }
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            convId = peerDeviceId,
            peerDeviceId = peerDeviceId,
            title = title,
            lastMessageTs = now,
        )
        dao.upsert(entity)
        return entity.toDomain()
    }

    suspend fun touchLast(convId: String, msgId: String, preview: String?, ts: Long, unreadDelta: Int) =
        dao.updateLast(convId, msgId, preview, ts, unreadDelta)

    suspend fun clearUnread(convId: String) = dao.clearUnread(convId)

    suspend fun setPinned(convId: String, pinned: Boolean) = dao.setPinned(convId, pinned)

    suspend fun delete(convId: String) = dao.delete(convId)
}

internal fun ConversationEntity.toDomain(): Conversation = Conversation(
    convId = convId,
    peerDeviceId = peerDeviceId,
    title = title,
    lastMessageId = lastMessageId,
    lastMessagePreview = lastMessagePreview,
    lastMessageTs = lastMessageTs,
    unreadCount = unreadCount,
    mutedUntilMs = mutedUntilMs,
    pinned = pinned,
)
