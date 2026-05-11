package com.nearlink.messenger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nearlink.messenger.core.model.MessageStatus
import com.nearlink.messenger.core.model.MessageType

/**
 * 消息表。`id` 用 ULID/UUIDv7（单调），与对端共享 —— 用于幂等去重。
 *
 * 注意：本表存的是 *已解密* 的本地内容；密文不入库（密文只在 outbox 中临时持有，发送/接收完成后删除）。
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["conv_id"],
            childColumns = ["conv_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conv_id", "created_at_ms"]),
        Index(value = ["status"]),
    ],
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "conv_id") val convId: String,
    @ColumnInfo(name = "sender_device_id") val senderDeviceId: String,
    @ColumnInfo(name = "recipient_device_id") val recipientDeviceId: String,

    @ColumnInfo(name = "type") val type: MessageType,
    @ColumnInfo(name = "status") val status: MessageStatus,

    @ColumnInfo(name = "is_outgoing") val isOutgoing: Boolean,

    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
    @ColumnInfo(name = "delivered_at_ms") val deliveredAtMs: Long? = null,
    @ColumnInfo(name = "read_at_ms") val readAtMs: Long? = null,

    @ColumnInfo(name = "reply_to_id") val replyToId: String? = null,
    @ColumnInfo(name = "edited_from_id") val editedFromId: String? = null,
    @ColumnInfo(name = "revoked") val revoked: Boolean = false,
    @ColumnInfo(name = "edited") val edited: Boolean = false,

    @ColumnInfo(name = "text") val text: String? = null,

    @ColumnInfo(name = "att_local_uri") val attachmentLocalUri: String? = null,
    @ColumnInfo(name = "att_remote_id") val attachmentRemoteId: String? = null,
    @ColumnInfo(name = "att_mime") val attachmentMime: String? = null,
    @ColumnInfo(name = "att_size") val attachmentSize: Long? = null,
    @ColumnInfo(name = "att_sha256") val attachmentSha256: String? = null,
    @ColumnInfo(name = "att_duration_ms") val attachmentDurationMs: Long? = null,
    @ColumnInfo(name = "att_w") val attachmentWidth: Int? = null,
    @ColumnInfo(name = "att_h") val attachmentHeight: Int? = null,

    /** JSON: `{ "👍": ["device_id_a", ...], ... }`，由 Repository 序列化。 */
    @ColumnInfo(name = "reactions_json") val reactionsJson: String? = null,
)
