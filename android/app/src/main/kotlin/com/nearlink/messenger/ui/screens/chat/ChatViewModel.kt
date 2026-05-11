package com.nearlink.messenger.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearlink.messenger.core.model.Message
import com.nearlink.messenger.domain.usecase.MarkReadUseCase
import com.nearlink.messenger.domain.usecase.ObserveMessagesUseCase
import com.nearlink.messenger.domain.usecase.SendMessageUseCase
import com.nearlink.messenger.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val convId: String,
    val title: String = "",
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedState: SavedStateHandle,
    observeMessages: ObserveMessagesUseCase,
    private val sendUseCase: SendMessageUseCase,
    private val markRead: MarkReadUseCase,
    private val contacts: ContactRepository,
) : ViewModel() {

    private val convId: String = savedState["convId"] ?: error("convId arg missing")

    private val _state = MutableStateFlow(ChatUiState(convId = convId))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    val messages: StateFlow<List<Message>> = observeMessages(convId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val contact = contacts.get(convId)
            _state.value = _state.value.copy(title = contact?.nickname ?: convId.take(8))
            markRead(convId)
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching { sendUseCase(convId, trimmed) }
        }
    }
}
