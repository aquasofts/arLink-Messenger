package com.nearlink.messenger.core.transport

/** 传输通道。同名常量也用作 outbox.preferredChannel。 */
enum class TransportChannel { BLUETOOTH, WIFI_LAN, SERVER, AUTO }

/** 传输层投递的载荷：已经是密文。 */
data class Envelope(
    val clientMsgId: String,
    val convId: String,
    val fromDeviceId: String,
    val toDeviceId: String,
    val alg: String,
    val nonce: ByteArray,
    val ephemeralPub: ByteArray,
    val ciphertext: ByteArray,
    val aad: ByteArray,
    val refMsgId: String? = null,
    val createdAtMs: Long,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is Envelope && clientMsgId == other.clientMsgId)

    override fun hashCode(): Int = clientMsgId.hashCode()
}

/** 投递结果。 */
sealed interface DeliveryAck {
    val clientMsgId: String

    data class Sent(override val clientMsgId: String, val channel: TransportChannel, val atMs: Long) : DeliveryAck
    data class Delivered(override val clientMsgId: String, val channel: TransportChannel, val atMs: Long) : DeliveryAck
    data class Queued(override val clientMsgId: String, val atMs: Long) : DeliveryAck       // 服务器已收，对端未在线
    data class Failed(override val clientMsgId: String, val reason: String, val retryable: Boolean) : DeliveryAck
}
