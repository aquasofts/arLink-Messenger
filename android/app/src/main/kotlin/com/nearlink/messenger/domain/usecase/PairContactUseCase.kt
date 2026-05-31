package com.nearlink.messenger.domain.usecase

import android.Manifest
import androidx.annotation.RequiresPermission
import com.nearlink.messenger.core.bluetooth.BluetoothEngine
import com.nearlink.messenger.core.bluetooth.BtHandshake
import com.nearlink.messenger.core.bluetooth.PeerIdentity
import com.nearlink.messenger.core.bluetooth.RfcommClient
import com.nearlink.messenger.core.crypto.SafetyNumber
import com.nearlink.messenger.core.model.Contact
import com.nearlink.messenger.core.model.TrustState
import com.nearlink.messenger.data.repository.ContactRepository
import com.nearlink.messenger.data.repository.IdentityRepository
import javax.inject.Inject

/**
 * 由发起端调用：连接 BluetoothDevice 地址 → 握手 → 计算安全码 → 返回临时 PairingResult。
 *
 * 用户在 UI 上人工核对安全码后，通过 [ConfirmSafetyNumberUseCase] 落库为 VERIFIED。
 */
class PairContactUseCase @Inject constructor(
    private val client: RfcommClient,
    private val handshake: BtHandshake,
    private val identity: IdentityRepository,
    private val bt: BluetoothEngine,
    private val contacts: ContactRepository,
) {
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT])
    suspend operator fun invoke(remoteAddress: String, myNickname: String?): PairingResult {
        val session = client.connect(remoteAddress)
        val peer: PeerIdentity = handshake.performAsInitiator(session.input, session.output, myNickname)
        // 注册到 BluetoothEngine，握手成功后即可走密文消息
        bt.onSessionEstablished(peer.deviceId, session)
        savePendingContact(peer)
        val mySelf = identity.publicIdentity()
        val safety = SafetyNumber.compute(mySelf.edPub, peer.edPub)
        return PairingResult(peer = peer, safetyNumber = safety)
    }

    private suspend fun savePendingContact(peer: PeerIdentity) {
        val now = System.currentTimeMillis()
        val existing = contacts.get(peer.deviceId)
        val sameKeys = existing?.pkIdentity?.contentEquals(peer.edPub) == true &&
            existing.pkX.contentEquals(peer.xPub)
        val trustState = when {
            existing == null -> TrustState.UNVERIFIED
            sameKeys -> existing.trustState
            else -> TrustState.CHANGED
        }
        contacts.upsert(
            Contact(
                deviceId = peer.deviceId,
                nickname = peer.nickname?.takeIf { it.isNotBlank() } ?: existing?.nickname ?: peer.deviceId.take(8),
                avatarUri = existing?.avatarUri,
                pkIdentity = peer.edPub,
                pkX = peer.xPub,
                trustState = trustState,
                createdAtMs = existing?.createdAtMs ?: now,
                updatedAtMs = now,
                lastSeenMs = now,
            )
        )
    }
}

data class PairingResult(
    val peer: PeerIdentity,
    val safetyNumber: String,
)
