package com.nearlink.messenger.ui.screens.pair

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearlink.messenger.core.crypto.SafetyNumber
import com.nearlink.messenger.core.model.TrustState
import com.nearlink.messenger.data.repository.ContactRepository
import com.nearlink.messenger.data.repository.ConversationRepository
import com.nearlink.messenger.data.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SafetyConfirmUiState(
    val peerDeviceId: String,
    val title: String = "",
    val safetyNumber: String? = null,
    val error: String? = null,
    val canConfirm: Boolean = false,
)

@HiltViewModel
class SafetyConfirmViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val contactRepo: ContactRepository,
    private val convRepo: ConversationRepository,
    private val identity: IdentityRepository,
) : ViewModel() {

    private val peerDeviceId: String = savedState["deviceId"] ?: error("deviceId arg missing")

    private val _state = MutableStateFlow(SafetyConfirmUiState(peerDeviceId = peerDeviceId))
    val state: StateFlow<SafetyConfirmUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val contact = contactRepo.get(peerDeviceId)
            if (contact == null) {
                _state.value = _state.value.copy(error = "找不到刚配对的联系人，请重新配对")
                return@launch
            }
            val me = identity.publicIdentity()
            _state.value = _state.value.copy(
                title = contact.nickname,
                safetyNumber = SafetyNumber.compute(me.edPub, contact.pkIdentity),
                canConfirm = true,
            )
        }
    }

    fun confirm(onDone: (convId: String) -> Unit) {
        viewModelScope.launch {
            val existing = contactRepo.get(peerDeviceId)
            if (existing == null) {
                _state.value = _state.value.copy(error = "联系人不存在，无法确认安全码")
                return@launch
            }
            contactRepo.setTrustState(existing.deviceId, TrustState.VERIFIED)
            convRepo.ensureForPeer(existing.deviceId, existing.nickname)
            onDone(existing.deviceId)
        }
    }
}
