package com.nearlink.messenger.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 蓝牙 RFCOMM 应用层（见 docs/bluetooth.md §6）。
 *
 * 物理帧由 BtFraming 处理（MAGIC|VER|TYPE|LEN|payload）；这里只定义 JSON payload schema。
 */
object BtPacketType {
    const val HELLO: Byte = 0x01
    const val HELLO_ACK: Byte = 0x02
    const val MSG: Byte = 0x10
    const val MSG_ACK: Byte = 0x11
    const val MSG_READ: Byte = 0x12
    const val MSG_TYPING: Byte = 0x13
    const val FILE_INIT: Byte = 0x20
    const val FILE_CHUNK: Byte = 0x21
    const val FILE_DONE: Byte = 0x22
    const val PING: Byte = 0xF0.toByte()
    const val PONG: Byte = 0xF1.toByte()
    const val BYE: Byte = 0xFF.toByte()
}

@Serializable
data class BtHelloPayload(
    @SerialName("device_id") val deviceId: String,
    @SerialName("pk_id_b64") val pkIdentityB64: String,     // Ed25519 公钥
    @SerialName("pk_x_b64") val pkXB64: String,             // X25519 公钥
    @SerialName("sig_b64") val sigB64: String,              // Ed25519(sk_id, "NL-PAIR" || pk_x)
    @SerialName("app_ver") val appVersion: String,
    val nickname: String? = null,                           // 可选，便于显示
)

@Serializable
data class BtHelloAckPayload(
    val ok: Boolean,
    val reason: String? = null,
)

/** RFCOMM 上的密文消息 payload —— 与 WS 的 [WireEncryptedMessage] 字段对齐。 */
@Serializable
data class BtMessagePayload(
    @SerialName("client_msg_id") val clientMsgId: String,
    @SerialName("conv_id") val convId: String,
    @SerialName("to_device_id") val toDeviceId: String,
    val alg: String,
    @SerialName("nonce_b64") val nonceB64: String,
    @SerialName("ephemeral_pub_b64") val ephemeralPubB64: String,
    @SerialName("ciphertext_b64") val ciphertextB64: String,
    @SerialName("ref_msg_id") val refMsgId: String? = null,
)

@Serializable
data class BtMsgAckPayload(
    @SerialName("client_msg_id") val clientMsgId: String,
    val status: String,                  // "received" | "rejected"
    val reason: String? = null,
)

@Serializable
data class BtReadPayload(
    @SerialName("conv_id") val convId: String,
    @SerialName("up_to_msg_id") val upToMsgId: String,
)

@Serializable
data class BtTypingPayload(
    @SerialName("conv_id") val convId: String,
    val state: String,                   // "start" | "stop"
)

@Serializable
data class BtFileInitPayload(
    @SerialName("file_id") val fileId: String,
    @SerialName("total_size") val totalSize: Long,
    @SerialName("sha256_b64") val sha256B64: String,
    @SerialName("chunk_size") val chunkSize: Int,
    val mime: String? = null,
)

@Serializable
data class BtFileDonePayload(
    @SerialName("file_id") val fileId: String,
    val ok: Boolean,
    val reason: String? = null,
)
