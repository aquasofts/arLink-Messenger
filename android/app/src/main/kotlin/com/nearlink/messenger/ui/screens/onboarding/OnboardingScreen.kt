package com.nearlink.messenger.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearlink.messenger.R
import com.nearlink.messenger.data.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val deviceId: String? = null,
    val ready: Boolean = false,
    val onboarded: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val identity: IdentityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state = _state.asStateFlow()

    fun bootstrap() {
        viewModelScope.launch {
            val alreadyOnboarded = identity.isOnboarded()
            val pub = identity.bootstrap()
            _state.value = OnboardingUiState(deviceId = pub.deviceId, ready = true, onboarded = alreadyOnboarded)
        }
    }

    fun finish() {
        viewModelScope.launch { identity.markOnboarded() }
    }
}

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.bootstrap() }
    LaunchedEffect(state.ready, state.onboarded) {
        if (state.ready && state.onboarded) onDone()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.onboarding_desc), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        state.deviceId?.let { Text("device_id: ${it.take(16)}…", style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(48.dp))
        Button(enabled = state.ready, onClick = {
            viewModel.finish()
            onDone()
        }) {
            Text(stringResource(R.string.onboarding_continue))
        }
    }
}
