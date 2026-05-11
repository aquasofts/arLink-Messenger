package com.nearlink.messenger.core.model

/**
 * 设备/联系人在某条通道上的在线状态。
 * 注意：UI 显示的"状态"是这三态的聚合（见 [PresenceState.aggregate]）。
 */
enum class PresenceChannel { BLUETOOTH, SERVER }

/** 聚合后的对外状态。 */
enum class AggregatedPresence { BT_ONLINE, SERVER_ONLINE, OFFLINE }

data class PresenceState(
    val deviceId: String,
    val btOnline: Boolean = false,
    val serverOnline: Boolean = false,
    val lastSeenMs: Long? = null,
) {
    fun aggregate(): AggregatedPresence = when {
        btOnline -> AggregatedPresence.BT_ONLINE
        serverOnline -> AggregatedPresence.SERVER_ONLINE
        else -> AggregatedPresence.OFFLINE
    }
}
