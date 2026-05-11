package com.nearlink.messenger.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 端到端加密后**密文内部**的明文结构（解密后才能看到）。
 * 与 docs/encryption.md §6 对齐。
 */
@Serializable
data class PlaintextEnvelope(
    val type: String,                              // "text" | "image" | "file" | "audio" | "revoke" | "edit" | "reaction" | "read" | "typing"
    @SerialName("client_msg_id") val clientMsgId: String,
    val ts: Long,
    val ref: PlaintextRef? = null,
    val body: PlaintextBody,
)

@Serializable
data class PlaintextRef(
    @SerialName("target_msg_id") val targetMsgId: String,
)

@Serializable
sealed interface PlaintextBody {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : PlaintextBody

    @Serializable
    @SerialName("image")
    data class Image(
        @SerialName("file_id") val fileId: String,
        val mime: String,
        @SerialName("sha256_b64") val sha256B64: String,
        @SerialName("size") val size: Long,
        @SerialName("w") val width: Int? = null,
        @SerialName("h") val height: Int? = null,
        @SerialName("thumb_b64") val thumbB64: String? = null,
    ) : PlaintextBody

    @Serializable
    @SerialName("file")
    data class File(
        @SerialName("file_id") val fileId: String,
        val mime: String,
        @SerialName("sha256_b64") val sha256B64: String,
        @SerialName("size") val size: Long,
        val name: String,
    ) : PlaintextBody

    @Serializable
    @SerialName("audio")
    data class Audio(
        @SerialName("file_id") val fileId: String,
        val mime: String,
        @SerialName("sha256_b64") val sha256B64: String,
        @SerialName("size") val size: Long,
        @SerialName("duration_ms") val durationMs: Long,
    ) : PlaintextBody

    @Serializable
    @SerialName("revoke")
    data class Revoke(
        @SerialName("target_msg_id") val targetMsgId: String,
    ) : PlaintextBody

    @Serializable
    @SerialName("edit")
    data class Edit(
        @SerialName("target_msg_id") val targetMsgId: String,
        @SerialName("new_text") val newText: String,
    ) : PlaintextBody

    @Serializable
    @SerialName("reaction")
    data class Reaction(
        @SerialName("target_msg_id") val targetMsgId: String,
        val emoji: String,
        val op: String,                            // "add" | "remove"
    ) : PlaintextBody

    @Serializable
    @SerialName("read")
    data class Read(
        @SerialName("up_to_msg_id") val upToMsgId: String,
    ) : PlaintextBody

    @Serializable
    @SerialName("typing")
    data class Typing(val state: String) : PlaintextBody  // "start" | "stop"
}
