package com.nearlink.messenger.ui.screens.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearlink.messenger.core.qr.QrContactCodec
import com.nearlink.messenger.data.repository.ContactRepository
import com.nearlink.messenger.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QrContactUiState(
    val payload: String = "",
    val importText: String = "",
    val status: String? = null,
)

@HiltViewModel
class QrContactViewModel @Inject constructor(
    private val codec: QrContactCodec,
    private val contacts: ContactRepository,
    private val conversations: ConversationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(QrContactUiState())
    val state: StateFlow<QrContactUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(payload = codec.encodeInvite())
        }
    }

    fun setImportText(text: String) {
        _state.value = _state.value.copy(importText = text)
    }

    fun importContact(onImported: (String) -> Unit) {
        val payload = _state.value.importText.trim()
        if (payload.isEmpty()) return
        viewModelScope.launch {
            val contact = codec.decodeContact(payload)
            if (contact == null) {
                _state.value = _state.value.copy(status = "二维码无效或签名验证失败")
                return@launch
            }
            val saved = contacts.upsertQrContact(contact)
            conversations.ensureForPeer(saved.deviceId, saved.nickname)
            _state.value = _state.value.copy(status = "已导入联系人：${saved.nickname}")
            onImported(saved.deviceId)
        }
    }
}
