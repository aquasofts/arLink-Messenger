package com.nearlink.messenger.data.repository

import com.nearlink.messenger.core.crypto.CryptoEngine
import com.nearlink.messenger.core.crypto.IdentityKeyStore
import com.nearlink.messenger.core.model.Message
import com.nearlink.messenger.core.model.MessageStatus
import com.nearlink.messenger.core.model.MessageType
import com.nearlink.messenger.core.protocol.NLJson
import com.nearlink.messenger.core.protocol.PlaintextBody
import com.nearlink.messenger.core.protocol.PlaintextEnvelope
import com.nearlink.messenger.core.protocol.PlaintextRef
import com.nearlink.messenger.core.transport.DeliveryAck
import com.nearlink.messenger.core.transport.Envelope
import com.nearlink.messenger.core.transport.TransportChannel
import com.nearlink.messenger.core.transport.TransportManager
import com.nearlink.messenger.data.local.dao.MessageDao
import com.nearlink.messenger.data.local.dao.OutboxDao
import com.nearlink.messenger.data.local.entity.MessageEntity
import com.nearlink.messenger.data.local.entity.OutboxEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息仓库：UI 与 UseCase 的入口。
 * 只暴露 domain.Message；密文与传输层细节内部封装。
 */
@Singleton
class MessageRepository @Inject constructor(
    private val msgDao: MessageDao,
    private val outboxDao: OutboxDao,
    private val crypto: CryptoEngine,
    private val identity: IdentityKeyStore,
    private val transport: TransportManager,
    private val convRepo: ConversationRepository,
    private val contactRepo: ContactRepository,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun observeConv(convId: String): Flow<List<Message>> =
        msgDao.observeConv(convId).map { list -> list.map { it.toDomain() } }

    suspend fun pageDesc(convId: String, limit: Int, offset: Int): List<Message> =
        msgDao.pageDesc(convId, limit, offset).map { it.toDomain() }

    /**
     * 发送一条文本消息。
     *  1. 生成 client_msg_id；
     *  2. 序列化 PlaintextEnvelope；
     *  3. CryptoEngine.seal 得到 [Envelope]；
     *  4. 入 messages（status=PENDING）；
     *  5. 入 outbox；
     *  6. 立刻尝试投递一次。
     */
    suspend fun sendText(convId: String, text: String, replyTo: String? = null): Message {
        val peer = contactRepo.get(convId) ?: error("contact not found: $convId")
        val pub = identity.loadPublic()
        val now = System.currentTimeMillis()
        val msgId = CryptoEngine.newClientMsgId()

        val plaintext = PlaintextEnvelope(
            type = "text",
            clientMsgId = msgId,
            ts = now,
            ref = replyTo?.let { PlaintextRef(it) },
            body = PlaintextBody.Text(text),
        )
        val plaintextBytes = json.encodeToString(plaintext).toByteArray()
        val envelope = crypto.seal(
            selfDeviceId = pub.deviceId,
            peerDeviceId = peer.deviceId,
            peerXPub = peer.pkX,
            convId = convId,
            clientMsgId = msgId,
            plaintext = plaintextBytes,
        )

        val entity = MessageEntity(
            id = msgId,
            convId = convId,
            senderDeviceId = pub.deviceId,
            recipientDeviceId = peer.deviceId,
            type = MessageType.TEXT,
            status = MessageStatus.PENDING,
            isOutgoing = true,
            createdAtMs = now,
            updatedAtMs = now,
            replyToId = replyTo,
            text = text,
        )
        msgDao.upsert(entity)
        convRepo.touchLast(convId, msgId, text.take(80), now, unreadDelta = 0)

        outboxDao.upsert(envelope.toOutbox(now))
        // 立即尝试一次（不等 Worker）
        attempt(envelope)

        return entity.toDomain()
    }

    suspend fun createQrTextEnvelope(convId: String, text: String, replyTo: String? = null): Envelope {
        val peer = contactRepo.get(convId) ?: error("contact not found: $convId")
        val pub = identity.loadPublic()
        val now = System.currentTimeMillis()
        val msgId = CryptoEngine.newClientMsgId()
        val plaintext = PlaintextEnvelope(
            type = "text",
            clientMsgId = msgId,
            ts = now,
            ref = replyTo?.let { PlaintextRef(it) },
            body = PlaintextBody.Text(text),
        )
        val envelope = crypto.seal(
            selfDeviceId = pub.deviceId,
            peerDeviceId = peer.deviceId,
            peerXPub = peer.pkX,
            convId = convId,
            clientMsgId = msgId,
            plaintext = json.encodeToString(plaintext).toByteArray(),
        )
        val entity = MessageEntity(
            id = msgId,
            convId = convId,
            senderDeviceId = pub.deviceId,
            recipientDeviceId = peer.deviceId,
            type = MessageType.TEXT,
            status = MessageStatus.PENDING,
            isOutgoing = true,
            createdAtMs = now,
            updatedAtMs = now,
            replyToId = replyTo,
            text = text,
        )
        msgDao.upsert(entity)
        convRepo.touchLast(convId, msgId, text.take(80), now, unreadDelta = 0)
        return envelope
    }

    suspend fun ingestFromQr(envelope: Envelope): QrIngestResult {
        val pub = identity.loadPublic()
        if (envelope.toDeviceId != pub.deviceId) return QrIngestResult.WRONG_RECIPIENT
        if (msgDao.getById(envelope.clientMsgId) != null) return QrIngestResult.DUPLICATE
        if (contactRepo.get(envelope.fromDeviceId) == null) return QrIngestResult.UNKNOWN_SENDER
        return if (ingest(envelope)) QrIngestResult.IMPORTED else QrIngestResult.DECRYPT_FAILED
    }

    /**
     * 单次发送尝试；UseCase 也可以直接调用。
     */
    suspend fun attempt(envelope: Envelope): DeliveryAck {
        msgDao.updateStatus(envelope.clientMsgId, MessageStatus.SENDING, System.currentTimeMillis())
        var last: DeliveryAck = DeliveryAck.Failed(envelope.clientMsgId, "no_attempt", retryable = true)
        transport.send(envelope).collect { ack ->
            last = ack
            when (ack) {
                is DeliveryAck.Sent -> msgDao.updateStatus(envelope.clientMsgId, MessageStatus.SENT, System.currentTimeMillis())
                is DeliveryAck.Delivered -> {
                    val ts = System.currentTimeMillis()
                    msgDao.updateStatus(envelope.clientMsgId, MessageStatus.DELIVERED, ts, deliveredAt = ts)
                    outboxDao.delete(envelope.clientMsgId)
                }
                is DeliveryAck.Queued -> {
                    msgDao.updateStatus(envelope.clientMsgId, MessageStatus.SENT, System.currentTimeMillis())
                    outboxDao.delete(envelope.clientMsgId) // 服务器已收，本地不再需要重试
                }
                is DeliveryAck.Failed -> {
                    if (!ack.retryable) {
                        msgDao.updateStatus(envelope.clientMsgId, MessageStatus.FAILED, System.currentTimeMillis())
                        outboxDao.delete(envelope.clientMsgId)
                    }
                }
            }
        }
        return last
    }

    /**
     * 处理一条入站密文 Envelope：解密 + 去重入库 + 更新会话。
     * 返回 true 表示新写入，false 表示重复。
     */
    suspend fun ingest(envelope: Envelope): Boolean {
        val pub = identity.loadPublic()
        if (envelope.toDeviceId != pub.deviceId) return false

        val sender = contactRepo.get(envelope.fromDeviceId) ?: return false  // 未配对的对端：丢弃
        val plaintextBytes = try {
            crypto.openWithPeer(pub.deviceId, envelope, sender.pkX)
        } catch (t: Throwable) {
            // 解密失败 → 标记 trust 变化由上层 UseCase 处理
            return false
        }
        val plaintext = json.decodeFromString(PlaintextEnvelope.serializer(), String(plaintextBytes))
        val now = System.currentTimeMillis()

        // 控制类消息走另一路（撤回/编辑/反应/typing/read）
        when (val body = plaintext.body) {
            is PlaintextBody.Revoke -> { msgDao.revoke(body.targetMsgId, now); return true }
            is PlaintextBody.Edit -> { msgDao.edit(body.targetMsgId, body.newText, now); return true }
            is PlaintextBody.Reaction -> {
                val target = msgDao.getById(body.targetMsgId) ?: return false
                val reactions: MutableMap<String, MutableList<String>> = parseReactions(target.reactionsJson)
                val list = reactions.getOrPut(body.emoji) { mutableListOf() }
                if (body.op == "add" && envelope.fromDeviceId !in list) list += envelope.fromDeviceId
                if (body.op == "remove") list -= envelope.fromDeviceId
                msgDao.setReactions(target.id, json.encodeToString(reactions as Map<String, List<String>>), now)
                return true
            }
            is PlaintextBody.Read -> {
                msgDao.markReadUpTo(envelope.convId, plaintext.ts, now)
                return true
            }
            is PlaintextBody.Typing -> return true     // 由 UI 直接订阅 typing 流，不入库
            else -> Unit
        }

        val entity = MessageEntity(
            id = plaintext.clientMsgId,
            convId = envelope.convId,
            senderDeviceId = envelope.fromDeviceId,
            recipientDeviceId = envelope.toDeviceId,
            type = mapType(plaintext.type),
            status = MessageStatus.DELIVERED,
            isOutgoing = false,
            createdAtMs = plaintext.ts,
            updatedAtMs = now,
            deliveredAtMs = now,
            replyToId = plaintext.ref?.targetMsgId,
            text = (plaintext.body as? PlaintextBody.Text)?.text,
            attachmentRemoteId = (plaintext.body as? PlaintextBody.Image)?.fileId
                ?: (plaintext.body as? PlaintextBody.File)?.fileId
                ?: (plaintext.body as? PlaintextBody.Audio)?.fileId,
            attachmentMime = (plaintext.body as? PlaintextBody.Image)?.mime
                ?: (plaintext.body as? PlaintextBody.File)?.mime
                ?: (plaintext.body as? PlaintextBody.Audio)?.mime,
            attachmentSize = (plaintext.body as? PlaintextBody.Image)?.size
                ?: (plaintext.body as? PlaintextBody.File)?.size
                ?: (plaintext.body as? PlaintextBody.Audio)?.size,
            attachmentSha256 = (plaintext.body as? PlaintextBody.Image)?.sha256B64
                ?: (plaintext.body as? PlaintextBody.File)?.sha256B64
                ?: (plaintext.body as? PlaintextBody.Audio)?.sha256B64,
            attachmentDurationMs = (plaintext.body as? PlaintextBody.Audio)?.durationMs,
            attachmentWidth = (plaintext.body as? PlaintextBody.Image)?.width,
            attachmentHeight = (plaintext.body as? PlaintextBody.Image)?.height,
        )
        val inserted = msgDao.insertWithDedup(entity)
        if (inserted) {
            convRepo.ensureForPeer(envelope.fromDeviceId, sender.nickname)
            convRepo.touchLast(
                envelope.convId,
                entity.id,
                entity.text?.take(80) ?: typePreview(entity.type),
                entity.createdAtMs,
                unreadDelta = 1,
            )
        }
        return inserted
    }

    suspend fun markRead(convId: String, upToTs: Long) {
        val now = System.currentTimeMillis()
        msgDao.markReadUpTo(convId, upToTs, now)
        convRepo.clearUnread(convId)
    }

    suspend fun revoke(messageId: String) {
        val msg = msgDao.getById(messageId) ?: return
        if (!msg.isOutgoing) return
        val now = System.currentTimeMillis()
        msgDao.revoke(messageId, now)

        // 同时给对端发一条 revoke 控制消息
        val peer = contactRepo.get(msg.convId) ?: return
        val pub = identity.loadPublic()
        val msgIdControl = CryptoEngine.newClientMsgId()
        val plaintext = PlaintextEnvelope(
            type = "revoke",
            clientMsgId = msgIdControl,
            ts = now,
            body = PlaintextBody.Revoke(messageId),
        )
        val env = crypto.seal(
            selfDeviceId = pub.deviceId,
            peerDeviceId = peer.deviceId,
            peerXPub = peer.pkX,
            convId = msg.convId,
            clientMsgId = msgIdControl,
            plaintext = json.encodeToString(plaintext).toByteArray(),
        )
        outboxDao.upsert(env.toOutbox(now))
        attempt(env)
    }

    suspend fun edit(messageId: String, newText: String) {
        val msg = msgDao.getById(messageId) ?: return
        if (!msg.isOutgoing) return
        val now = System.currentTimeMillis()
        msgDao.edit(messageId, newText, now)
        val peer = contactRepo.get(msg.convId) ?: return
        val pub = identity.loadPublic()
        val msgIdControl = CryptoEngine.newClientMsgId()
        val plaintext = PlaintextEnvelope(
            type = "edit",
            clientMsgId = msgIdControl,
            ts = now,
            body = PlaintextBody.Edit(messageId, newText),
        )
        val env = crypto.seal(pub.deviceId, peer.deviceId, peer.pkX, msg.convId, msgIdControl,
            json.encodeToString(plaintext).toByteArray())
        outboxDao.upsert(env.toOutbox(now))
        attempt(env)
    }

    /**
     * 由 [com.nearlink.messenger.worker.MessageRetryWorker] 调用。
     */
    suspend fun retryDue(now: Long, limit: Int = 16) {
        val due = outboxDao.pickDue(now, limit)
        for (item in due) {
            val env = item.toEnvelope(myDeviceId = identity.loadPublic().deviceId)
            val ack = attempt(env)
            if (ack is DeliveryAck.Failed && ack.retryable) {
                val nextDelay = backoff(item.attempts)
                outboxDao.reschedule(item.clientMsgId, now + nextDelay)
            }
        }
    }

    private fun backoff(attempts: Int): Long = when (attempts) {
        0 -> 5_000L
        1 -> 15_000L
        2 -> 60_000L
        3 -> 5 * 60_000L
        4 -> 30 * 60_000L
        else -> 60 * 60_000L
    }

    private fun parseReactions(jsonStr: String?): MutableMap<String, MutableList<String>> {
        if (jsonStr.isNullOrBlank()) return mutableMapOf()
        return runCatching {
            json.decodeFromString<Map<String, List<String>>>(jsonStr)
                .mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
        }.getOrDefault(mutableMapOf())
    }

    private fun mapType(type: String): MessageType = when (type) {
        "text" -> MessageType.TEXT
        "image" -> MessageType.IMAGE
        "file" -> MessageType.FILE
        "audio" -> MessageType.AUDIO
        "revoke" -> MessageType.REVOKE
        "edit" -> MessageType.EDIT
        "reaction" -> MessageType.REACTION
        "read" -> MessageType.READ_RECEIPT
        "typing" -> MessageType.TYPING
        else -> MessageType.UNKNOWN
    }

    private fun typePreview(type: MessageType) = when (type) {
        MessageType.IMAGE -> "[图片]"
        MessageType.FILE -> "[文件]"
        MessageType.AUDIO -> "[语音]"
        else -> ""
    }
}

enum class QrIngestResult { IMPORTED, DUPLICATE, UNKNOWN_SENDER, WRONG_RECIPIENT, DECRYPT_FAILED }

internal fun MessageEntity.toDomain(): Message = Message(
    id = id,
    convId = convId,
    senderDeviceId = senderDeviceId,
    recipientDeviceId = recipientDeviceId,
    type = type,
    status = status,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
    deliveredAtMs = deliveredAtMs,
    readAtMs = readAtMs,
    isOutgoing = isOutgoing,
    replyToId = replyToId,
    editedFromId = editedFromId,
    revoked = revoked,
    edited = edited,
    text = text,
    attachmentLocalUri = attachmentLocalUri,
    attachmentRemoteId = attachmentRemoteId,
    attachmentMime = attachmentMime,
    attachmentSize = attachmentSize,
    attachmentSha256 = attachmentSha256,
    attachmentDurationMs = attachmentDurationMs,
    attachmentWidth = attachmentWidth,
    attachmentHeight = attachmentHeight,
)

internal fun Envelope.toOutbox(now: Long): OutboxEntity = OutboxEntity(
    clientMsgId = clientMsgId,
    convId = convId,
    toDeviceId = toDeviceId,
    alg = alg,
    nonce = nonce,
    ephemeralPub = ephemeralPub,
    ciphertext = ciphertext,
    aad = aad,
    preferredChannel = "AUTO",
    refMsgId = refMsgId,
    nextAttemptAtMs = now,
    createdAtMs = now,
)

internal fun OutboxEntity.toEnvelope(myDeviceId: String): Envelope = Envelope(
    clientMsgId = clientMsgId,
    convId = convId,
    fromDeviceId = myDeviceId,
    toDeviceId = toDeviceId,
    alg = alg,
    nonce = nonce,
    ephemeralPub = ephemeralPub,
    ciphertext = ciphertext,
    aad = aad,
    refMsgId = refMsgId,
    createdAtMs = createdAtMs,
)
