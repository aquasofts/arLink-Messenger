package com.nearlink.messenger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [Index("peer_device_id", unique = true)],
)
data class ConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "conv_id") val convId: String,
    @ColumnInfo(name = "peer_device_id") val peerDeviceId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "last_message_id") val lastMessageId: String? = null,
    @ColumnInfo(name = "last_message_preview") val lastMessagePreview: String? = null,
    @ColumnInfo(name = "last_message_ts") val lastMessageTs: Long = 0L,
    @ColumnInfo(name = "unread_count") val unreadCount: Int = 0,
    @ColumnInfo(name = "muted_until_ms") val mutedUntilMs: Long? = null,
    @ColumnInfo(name = "pinned") val pinned: Boolean = false,
)
