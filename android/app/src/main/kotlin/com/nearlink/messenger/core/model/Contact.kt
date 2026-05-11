package com.nearlink.messenger.core.model

/**
 * 联系人的信任态：
 *  - UNVERIFIED   : 仅 BLE 发现过，未走 safety_number 核对
 *  - VERIFIED     : 双方核对过安全码
 *  - CHANGED      : 对端长期公钥发生变化，需要重新核对
 *  - BLOCKED      : 用户主动拉黑
 */
enum class TrustState { UNVERIFIED, VERIFIED, CHANGED, BLOCKED }

/**
 * 联系人。`deviceId` 即对端身份指纹的 base32 截断；`pkIdentity`/`pkX` 为 Ed25519/X25519 公钥（原始字节）。
 */
data class Contact(
    val deviceId: String,
    val nickname: String,
    val avatarUri: String? = null,
    val pkIdentity: ByteArray,
    val pkX: ByteArray,
    val trustState: TrustState = TrustState.UNVERIFIED,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val lastSeenMs: Long? = null,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is Contact && other.deviceId == deviceId)

    override fun hashCode(): Int = deviceId.hashCode()
}
