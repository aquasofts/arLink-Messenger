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
) {
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT])
    suspend operator fun invoke(remoteAddress: String, myNickname: String?): PairingResult {
        val session = client.connect(remoteAddress)
        val peer: PeerIdentity = handshake.performAsInitiator(session.input, session.output, myNickname)
        // 注册到 BluetoothEngine，握手成功后即可走密文消息
        bt.onSessionEstablished(peer.deviceId, session)
        val mySelf = identity.publicIdentity()
        val safety = SafetyNumber.compute(mySelf.edPub, peer.edPub)
        return PairingResult(peer = peer, safetyNumber = safety)
    }
}

data class PairingResult(
    val peer: PeerIdentity,
    val safetyNumber: String,
)
