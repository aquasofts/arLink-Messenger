package com.nearlink.messenger.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * WebSocket 帧（与 docs/protocol.md §3 一致）。
 *
 * payload 用 JsonElement 透传，按 [type] 在 Engine 层分派到具体子类型。
 */
@Serializable
data class WireFrame(
    val v: Int = 1,
    val type: String,
    val id: String,
    val ts: Long,
    val from: String? = null,
    val to: String? = null,
    val payload: JsonObject? = null,
)

object WireFrameTypes {
    const val SERVER_HELLO = "server_hello"
    const val PING = "ping"
    const val PONG = "pong"
    const val PRESENCE_SUB = "presence_sub"
    const val PRESENCE_UPDATE = "presence_update"
    const val MSG_SEND = "msg_send"
    const val MSG_RELAY = "msg_relay"
    const val MSG_ACK = "msg_ack"
    const val MSG_DELIVERED = "msg_delivered"
    const val MSG_READ = "msg_read"
    const val MSG_TYPING = "msg_typing"
    const val MSG_REVOKE = "msg_revoke"
    const val MSG_EDIT = "msg_edit"
    const val MSG_REACTION = "msg_reaction"
    const val PULL_OFFLINE = "pull_offline"
    const val PULL_OFFLINE_CHUNK = "pull_offline_chunk"
    const val ERROR = "error"
}

/** msg_send / msg_relay 的 payload。 */
@Serializable
data class WireEncryptedMessage(
    @SerialName("client_msg_id") val clientMsgId: String,
    @SerialName("conv_id") val convId: String,
    @SerialName("to_device_id") val toDeviceId: String,
    val kind: String = "encrypted",
    val alg: String,
    @SerialName("nonce_b64") val nonceB64: String,
    @SerialName("ephemeral_pub_b64") val ephemeralPubB64: String,
    @SerialName("ciphertext_b64") val ciphertextB64: String,
    @SerialName("aad_b64") val aadB64: String? = null,
    @SerialName("ref_msg_id") val refMsgId: String? = null,
    val size: Int,
)

@Serializable
data class WireServerHello(
    @SerialName("server_time") val serverTime: Long,
    @SerialName("session_id") val sessionId: String,
)

@Serializable
data class WirePresenceUpdate(
    @SerialName("device_id") val deviceId: String,
    val state: String,                       // "bt_online" | "server_online" | "offline"
    @SerialName("last_seen") val lastSeen: Long? = null,
)

@Serializable
data class WireMsgAck(
    @SerialName("client_msg_id") val clientMsgId: String,
    @SerialName("server_msg_id") val serverMsgId: String? = null,
    val status: String,                       // "queued" | "relayed" | "rejected"
    val reason: String? = null,
)

@Serializable
data class WireMsgDelivered(
    @SerialName("client_msg_id") val clientMsgId: String = "",
    @SerialName("server_msg_id") val serverMsgId: String? = null,
    @SerialName("to_device_id") val toDeviceId: String,
) {
    val effectiveClientMsgId: String get() = clientMsgId.ifBlank { serverMsgId.orEmpty() }
}

@Serializable
data class WirePullOffline(
    @SerialName("since_ts") val sinceTs: Long,
)

@Serializable
data class WirePullOfflineChunk(
    val messages: List<JsonElement>,
    @SerialName("has_more") val hasMore: Boolean,
    val cursor: String? = null,
)

@Serializable
data class WireError(
    val code: String,
    val message: String,
    @SerialName("ref_id") val refId: String? = null,
)
