package com.nearlink.messenger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本机身份与会话密钥缓存。
 * - id="self_identity" : 本机 Ed25519 + X25519 公钥 (私钥在 Keystore，不入此表)
 * - id="root:<peer>"   : 与某联系人协商出的 root_key (如选择持久化)
 */
@Entity(tableName = "keys")
data class KeyEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "kind") val kind: String,           // "identity" | "root"
    @ColumnInfo(name = "blob") val blob: ByteArray,        // Keystore 包裹后的密文
    @ColumnInfo(name = "alg") val alg: String,             // "ed25519+x25519" | "hkdf-sha256"
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "rotated_at_ms") val rotatedAtMs: Long? = null,
)
