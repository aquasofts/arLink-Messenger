package com.nearlink.messenger.domain.usecase

import com.nearlink.messenger.core.bluetooth.PeerIdentity
import com.nearlink.messenger.core.model.Contact
import com.nearlink.messenger.core.model.TrustState
import com.nearlink.messenger.data.repository.ContactRepository
import javax.inject.Inject

/**
 * 用户在两台设备上人眼核对安全码后调用。
 * 写入联系人，trust = VERIFIED。
 */
class ConfirmSafetyNumberUseCase @Inject constructor(
    private val contacts: ContactRepository,
) {
    suspend operator fun invoke(peer: PeerIdentity, displayNickname: String? = null): Contact {
        val now = System.currentTimeMillis()
        val existing = contacts.get(peer.deviceId)
        val merged = Contact(
            deviceId = peer.deviceId,
            nickname = displayNickname ?: peer.nickname ?: peer.deviceId.take(6),
            avatarUri = existing?.avatarUri,
            pkIdentity = peer.edPub,
            pkX = peer.xPub,
            trustState = TrustState.VERIFIED,
            createdAtMs = existing?.createdAtMs ?: now,
            updatedAtMs = now,
            lastSeenMs = now,
        )
        contacts.upsert(merged)
        return merged
    }
}
