package com.nearlink.messenger.core.lan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LanPeer(
    @SerialName("device_id") val deviceId: String,
    val host: String,
    val port: Int,
    val nickname: String? = null,
    @SerialName("ed_pub") val edPub: String? = null,
    @SerialName("x_pub") val xPub: String? = null,
    @SerialName("last_seen_ms") val lastSeenMs: Long = System.currentTimeMillis(),
)

@Serializable
data class LanHello(
    val version: Int = 1,
    @SerialName("device_id") val deviceId: String,
    val port: Int,
    val nickname: String? = null,
)
