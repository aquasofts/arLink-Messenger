package com.nearlink.messenger.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearlink.messenger.core.crypto.SafetyNumber
import com.nearlink.messenger.data.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val deviceId: String = "",
    val fingerprint: String = "",
    val nickname: String = "",
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val identity: IdentityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    val nickname = identity.nickname.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val pub = identity.publicIdentity()
            // 用与自己同公钥再算一次"自指"安全码，便于展示稳定指纹
            val fp = SafetyNumber.compute(pub.edPub, pub.edPub).split(' ').take(6).joinToString(" ")
            _state.value = ProfileUiState(deviceId = pub.deviceId, fingerprint = fp, nickname = "")
        }
    }

    fun setNickname(name: String) = viewModelScope.launch { identity.setNickname(name) }
}
