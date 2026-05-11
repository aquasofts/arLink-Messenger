package com.nearlink.messenger.ui.screens.pair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearlink.messenger.core.bluetooth.PeerIdentity
import com.nearlink.messenger.data.repository.ContactRepository
import com.nearlink.messenger.data.repository.ConversationRepository
import com.nearlink.messenger.domain.usecase.ConfirmSafetyNumberUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SafetyConfirmViewModel @Inject constructor(
    private val contactRepo: ContactRepository,
    private val convRepo: ConversationRepository,
    private val confirmUseCase: ConfirmSafetyNumberUseCase,
) : ViewModel() {

    /**
     * 真正的握手数据（PeerIdentity）由 PairViewModel 在 PairContactUseCase 后持有，
     * SafetyNumberScreen 跳转此处时只携带 deviceId。简化：从已存在的临时 contact 行直接读公钥。
     * 这里假设 PairViewModel 已在 onSessionEstablished 时把对端登记进 BluetoothEngine。
     * 实际生产里建议把 PeerIdentity 通过 SavedStateHandle 或 ViewModel 单例传递。
     */
    fun confirm(peerDeviceId: String, onDone: (convId: String) -> Unit) {
        viewModelScope.launch {
            val existing = contactRepo.get(peerDeviceId)
            val contact = if (existing != null) {
                // 升级 trust 状态
                contactRepo.setTrustState(existing.deviceId, com.nearlink.messenger.core.model.TrustState.VERIFIED)
                existing
            } else {
                // 兜底：若尚未入库（PairViewModel 路径异常），仅退出
                onDone(peerDeviceId); return@launch
            }
            convRepo.ensureForPeer(contact.deviceId, contact.nickname)
            onDone(contact.deviceId)
        }
    }
}
