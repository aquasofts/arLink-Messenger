package com.nearlink.messenger.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearlink.messenger.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
) : ViewModel() {

    val serverUrl: StateFlow<String?> = repo.serverUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val dbEncryption: StateFlow<Boolean> = repo.dbEncryptionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun saveServerUrl(url: String?) = viewModelScope.launch { repo.setServerUrl(url?.takeIf { it.isNotBlank() }) }
    fun setDbEncryption(enabled: Boolean) = viewModelScope.launch { repo.setDbEncryption(enabled) }
}
