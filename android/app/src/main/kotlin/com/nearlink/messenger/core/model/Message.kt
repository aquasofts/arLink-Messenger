package com.nearlink.messenger.core.model

/**
 * 内核中的 Message（由 Repository 提供给 UI 层）。
 * 此处不混用 Room 注解，与 [com.nearlink.messenger.data.local.entity.MessageEntity] 解耦，便于演进与单测。
 */
data class Message(
    val id: String,                 // = clientMsgId (UUIDv7/ULID)，与对端使用同一个 id
    val convId: String,
    val senderDeviceId: String,
    val recipientDeviceId: String,
    val type: MessageType,
    val status: MessageStatus,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val deliveredAtMs: Long? = null,
    val readAtMs: Long? = null,
    val isOutgoing: Boolean,
    val replyToId: String? = null,
    val editedFromId: String? = null,
    val revoked: Boolean = false,
    val edited: Boolean = false,

    // 解密后的明文 body —— 为不同 MessageType 渲染不同 UI
    val text: String? = null,
    val attachmentLocalUri: String? = null,
    val attachmentRemoteId: String? = null,
    val attachmentMime: String? = null,
    val attachmentSize: Long? = null,
    val attachmentSha256: String? = null,
    val attachmentDurationMs: Long? = null, // 语音
    val attachmentWidth: Int? = null,        // 图片
    val attachmentHeight: Int? = null,
    val reactions: Map<String, List<String>> = emptyMap(), // emoji -> [deviceId, ...]
)
