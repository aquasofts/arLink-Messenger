package com.nearlink.messenger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 出站队列：每条 *待投递* 的密文都在此暂存，由 MessageRetryWorker 周期消费。
 * 一旦任意通道返回 Sent/Delivered/Queued 即从该表删除。
 */
@Entity(
    tableName = "outbox",
    indices = [Index("next_attempt_at_ms")],
)
data class OutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "client_msg_id") val clientMsgId: String,
    @ColumnInfo(name = "conv_id") val convId: String,
    @ColumnInfo(name = "to_device_id") val toDeviceId: String,
    @ColumnInfo(name = "alg") val alg: String,
    @ColumnInfo(name = "nonce") val nonce: ByteArray,
    @ColumnInfo(name = "ephemeral_pub") val ephemeralPub: ByteArray,
    @ColumnInfo(name = "ciphertext") val ciphertext: ByteArray,
    @ColumnInfo(name = "aad") val aad: ByteArray,
    @ColumnInfo(name = "preferred_channel") val preferredChannel: String, // "BLUETOOTH" | "SERVER" | "AUTO"
    @ColumnInfo(name = "ref_msg_id") val refMsgId: String? = null,
    @ColumnInfo(name = "attempts") val attempts: Int = 0,
    @ColumnInfo(name = "next_attempt_at_ms") val nextAttemptAtMs: Long,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
)
