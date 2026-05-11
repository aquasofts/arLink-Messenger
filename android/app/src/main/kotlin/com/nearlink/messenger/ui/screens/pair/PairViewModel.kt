package com.nearlink.messenger.ui.screens.pair

import android.Manifest
import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearlink.messenger.core.bluetooth.DiscoveredDevice
import com.nearlink.messenger.domain.usecase.DiscoverPeersUseCase
import com.nearlink.messenger.domain.usecase.PairContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
) : ViewModel() {

    private val _state = MutableStateFlow(PairUiState())
    val state: StateFlow<PairUiState> = _state.asStateFlow()

    private val nearbyFlow = MutableStateFlow<List<DiscoveredDevice>>(emptyList())

    init {
        // 把流式扫描结果汇成列表
        viewModelScope.launch {
            @SuppressLint("MissingPermission")
            discover.observeNearby().collect { d ->
                val current = nearbyFlow.value
                if (current.none { it.address == d.address }) {
                    nearbyFlow.value = current + d
                    _state.value = _state.value.copy(nearby = nearbyFlow.value)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() = viewModelScope.launch {
        runCatching { discover.startAdvertising() }
            .onFailure { _state.value = _state.value.copy(error = it.message) }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() = discover.stopAdvertising()

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

    override fun onCleared() {
        stopAdvertising()
        super.onCleared()
    }
}
