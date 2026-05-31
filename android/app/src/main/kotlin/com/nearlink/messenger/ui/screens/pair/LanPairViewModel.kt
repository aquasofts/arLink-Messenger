package com.nearlink.messenger.ui.screens.pair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearlink.messenger.core.crypto.CryptoUtils
import com.nearlink.messenger.core.lan.LanPeer
import com.nearlink.messenger.core.lan.LanTransport
import com.nearlink.messenger.core.model.Contact
import com.nearlink.messenger.core.model.TrustState
import com.nearlink.messenger.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LanPairUiState(
    val peers: List<LanPeer> = emptyList(),
    val error: String? = null,
    val pairedPeerDeviceId: String? = null,
)

@HiltViewModel
class LanPairViewModel @Inject constructor(
    private val lan: LanTransport,
    private val contacts: ContactRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LanPairUiState())
    val state: StateFlow<LanPairUiState> = _state.asStateFlow()

    init {
        lan.start()
        viewModelScope.launch {
            lan.peers().collect { peer ->
                val merged = (_state.value.peers.filterNot { it.deviceId == peer.deviceId } + peer)
                    .sortedBy { it.deviceId }
                _state.value = _state.value.copy(peers = merged)
            }
        }
    }

    fun pair(peer: LanPeer) {
        viewModelScope.launch {
            val edPub = runCatching { peer.edPub?.let(CryptoUtils::unb64) }.getOrNull()
            val xPub = runCatching { peer.xPub?.let(CryptoUtils::unb64) }.getOrNull()
            if (edPub == null || xPub == null) {
                _state.value = _state.value.copy(error = "该设备没有广播完整身份，请让对方升级或重新打开局域网配对")
                return@launch
            }
            val expected = CryptoUtils.base32Lower(CryptoUtils.sha256(edPub)).take(24)
            if (expected != peer.deviceId) {
                _state.value = _state.value.copy(error = "该设备身份校验失败")
                return@launch
            }
            val now = System.currentTimeMillis()
            val existing = contacts.get(peer.deviceId)
            val sameKeys = existing?.pkIdentity?.contentEquals(edPub) == true && existing.pkX.contentEquals(xPub)
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
                    pkIdentity = edPub,
                    pkX = xPub,
                    trustState = trustState,
                    createdAtMs = existing?.createdAtMs ?: now,
                    updatedAtMs = now,
                    lastSeenMs = now,
                )
            )
            _state.value = _state.value.copy(pairedPeerDeviceId = peer.deviceId)
        }
    }
}
