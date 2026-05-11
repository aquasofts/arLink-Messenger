package com.nearlink.messenger.core.qr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QrContactInvite(
    val version: Int = 1,
    @SerialName("device_id") val deviceId: String,
    val nickname: String? = null,
    @SerialName("ed_pub") val edPub: String,
    @SerialName("x_pub") val xPub: String,
    @SerialName("created_at_ms") val createdAtMs: Long,
    val signature: String,
)

@Serializable
data class QrEnvelopePacket(
    val version: Int = 1,
    @SerialName("client_msg_id") val clientMsgId: String,
    @SerialName("conv_id") val convId: String,
    @SerialName("from_device_id") val fromDeviceId: String,
    @SerialName("to_device_id") val toDeviceId: String,
    val alg: String,
    val nonce: String,
    @SerialName("ephemeral_pub") val ephemeralPub: String,
    val ciphertext: String,
    val aad: String,
    @SerialName("ref_msg_id") val refMsgId: String? = null,
    @SerialName("created_at_ms") val createdAtMs: Long,
    @SerialName("sender_name") val senderName: String? = null,
)
