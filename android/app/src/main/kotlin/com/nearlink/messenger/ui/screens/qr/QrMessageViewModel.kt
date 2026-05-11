package com.nearlink.messenger.ui.screens.qr

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearlink.messenger.core.qr.QrEnvelopeCodec
import com.nearlink.messenger.data.repository.MessageRepository
import com.nearlink.messenger.data.repository.QrIngestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QrMessageUiState(
    val text: String = "",
    val payload: String = "",
    val importText: String = "",
    val status: String? = null,
)

@HiltViewModel
class QrMessageViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val messages: MessageRepository,
    private val codec: QrEnvelopeCodec,
) : ViewModel() {
    private val convId: String = savedState["convId"] ?: error("convId arg missing")
    private val _state = MutableStateFlow(QrMessageUiState())
    val state: StateFlow<QrMessageUiState> = _state.asStateFlow()

    fun setText(text: String) {
        _state.value = _state.value.copy(text = text)
    }

    fun setImportText(text: String) {
        _state.value = _state.value.copy(importText = text)
    }

    fun generate() {
        val text = _state.value.text.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val envelope = runCatching { messages.createQrTextEnvelope(convId, text) }.getOrNull()
            _state.value = if (envelope == null) {
                _state.value.copy(status = "生成失败：联系人不存在")
            } else {
                _state.value.copy(payload = codec.encode(envelope), status = "已生成端到端加密二维码消息")
            }
        }
    }

    fun importMessage() {
        val envelope = codec.decode(_state.value.importText.trim())
        if (envelope == null) {
            _state.value = _state.value.copy(status = "二维码消息无效")
            return
        }
        viewModelScope.launch {
            val result = runCatching { messages.ingestFromQr(envelope) }.getOrNull()
            val status = when (result) {
                QrIngestResult.IMPORTED -> "已导入消息"
                QrIngestResult.DUPLICATE -> "消息已存在"
                QrIngestResult.UNKNOWN_SENDER -> "未知发送者，请先导入联系人二维码"
                QrIngestResult.WRONG_RECIPIENT -> "这条消息不是发给本机的"
                QrIngestResult.DECRYPT_FAILED -> "解密失败或密文被篡改"
                null -> "导入失败，密文格式或本地会话状态异常"
            }
            _state.value = _state.value.copy(status = status)
        }
    }
}
