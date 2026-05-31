package com.nearlink.messenger.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearlink.messenger.core.lan.LanTransport
import com.nearlink.messenger.core.model.Conversation
import com.nearlink.messenger.data.repository.MessageRepository
import com.nearlink.messenger.data.repository.SettingsRepository
import com.nearlink.messenger.domain.usecase.ObserveConversationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeConversations: ObserveConversationsUseCase,
    private val messages: MessageRepository,
    private val lan: LanTransport,
    private val settings: SettingsRepository,
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> = observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        messages.start()
        lan.start()
        viewModelScope.launch { settings.connectIfConfigured() }
    }
}
