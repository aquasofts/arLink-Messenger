package com.nearlink.messenger.core.model

/**
 * 单聊会话。convId 直接使用对端 deviceId（v2 群聊需要换成独立 id）。
 */
data class Conversation(
    val convId: String,
    val peerDeviceId: String,
    val title: String,
    val lastMessageId: String? = null,
    val lastMessagePreview: String? = null,
    val lastMessageTs: Long = 0L,
    val unreadCount: Int = 0,
    val mutedUntilMs: Long? = null,
    val pinned: Boolean = false,
)
