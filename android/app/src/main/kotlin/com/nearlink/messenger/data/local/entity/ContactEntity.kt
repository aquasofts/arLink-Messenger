package com.nearlink.messenger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nearlink.messenger.core.model.TrustState

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "nickname") val nickname: String,
    @ColumnInfo(name = "avatar_uri") val avatarUri: String? = null,
    @ColumnInfo(name = "pk_identity") val pkIdentity: ByteArray,
    @ColumnInfo(name = "pk_x") val pkX: ByteArray,
    @ColumnInfo(name = "trust_state") val trustState: TrustState,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
    @ColumnInfo(name = "last_seen_ms") val lastSeenMs: Long? = null,
    @ColumnInfo(name = "blocked") val blocked: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is ContactEntity && deviceId == other.deviceId)
    override fun hashCode(): Int = deviceId.hashCode()
}
