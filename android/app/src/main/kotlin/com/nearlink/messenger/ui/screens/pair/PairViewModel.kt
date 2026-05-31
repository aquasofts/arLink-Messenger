package com.nearlink.messenger.ui.screens.pair

import android.Manifest
import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearlink.messenger.core.bluetooth.BluetoothEngine
import com.nearlink.messenger.core.bluetooth.DiscoveredDevice
import com.nearlink.messenger.core.bluetooth.PeerIdentity
import com.nearlink.messenger.core.bluetooth.RfcommServer
import com.nearlink.messenger.core.bluetooth.BtHandshake
import com.nearlink.messenger.core.crypto.SafetyNumber
import com.nearlink.messenger.core.model.Contact
import com.nearlink.messenger.core.model.TrustState
import com.nearlink.messenger.data.repository.ContactRepository
import com.nearlink.messenger.data.repository.IdentityRepository
import com.nearlink.messenger.domain.usecase.DiscoverPeersUseCase
import com.nearlink.messenger.domain.usecase.PairContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PairUiState(
    val nearby: List<DiscoveredDevice> = emptyList(),
    val pairing: Boolean = false,
    val error: String? = null,
    val pairedPeerDeviceId: String? = null,    // 跳转到 SafetyNumber 用
    val safetyNumber: String? = null,
)

@HiltViewModel
class PairViewModel @Inject constructor(
    private val discover: DiscoverPeersUseCase,
    private val pair: PairContactUseCase,
    private val server: RfcommServer,
    private val handshake: BtHandshake,
    private val bt: BluetoothEngine,
    private val contacts: ContactRepository,
    private val identity: IdentityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PairUiState())
    val state: StateFlow<PairUiState> = _state.asStateFlow()

    private val nearbyFlow = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    private var discoveryJob: Job? = null
    private var acceptJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startAdvertising() = viewModelScope.launch {
        runCatching { discover.startAdvertising() }
            .onFailure { _state.value = _state.value.copy(error = it.message) }
        if (discoveryJob == null) {
            discoveryJob = viewModelScope.launch {
                try {
                    discover.observeNearby().collect { d ->
                        val current = nearbyFlow.value
                        if (current.none { it.address == d.address }) {
                            nearbyFlow.value = current + d
                            _state.value = _state.value.copy(nearby = nearbyFlow.value)
                        }
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    _state.value = _state.value.copy(error = t.message)
                }
            }
        }
        if (acceptJob == null) {
            acceptJob = viewModelScope.launch {
                try {
                    server.listen().collect { session ->
                        launchCatchingSession(session)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    _state.value = _state.value.copy(error = t.message)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        discoveryJob?.cancel()
        discoveryJob = null
        acceptJob?.cancel()
        acceptJob = null
        server.stop()
        discover.stopAdvertising()
    }

    @SuppressLint("MissingPermission")
    fun pairWith(address: String, myNickname: String?) = viewModelScope.launch {
        _state.value = _state.value.copy(pairing = true, error = null)
        runCatching { pair(address, myNickname) }
            .onSuccess { result ->
                _state.value = _state.value.copy(
                    pairing = false,
                    pairedPeerDeviceId = result.peer.deviceId,
                    safetyNumber = result.safetyNumber,
                )
            }
            .onFailure { _state.value = _state.value.copy(pairing = false, error = it.message) }
    }

    private fun launchCatchingSession(session: com.nearlink.messenger.core.bluetooth.BtSession) {
        viewModelScope.launch {
            runCatching {
                val peer = handshake.performAsAcceptor(session.input, session.output, nickname = null)
                val safety = savePeerAndComputeSafety(peer)
                bt.onSessionEstablished(peer.deviceId, session)
                _state.value = _state.value.copy(
                    pairing = false,
                    pairedPeerDeviceId = peer.deviceId,
                    safetyNumber = safety,
                )
            }.onFailure {
                runCatching { session.close() }
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    private suspend fun savePeerAndComputeSafety(peer: PeerIdentity): String {
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
        val me = identity.publicIdentity()
        return SafetyNumber.compute(me.edPub, peer.edPub)
    }

    override fun onCleared() {
        stopAdvertising()
        super.onCleared()
    }
}
