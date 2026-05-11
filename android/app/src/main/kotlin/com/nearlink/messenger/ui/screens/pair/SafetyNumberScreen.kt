package com.nearlink.messenger.ui.screens.pair

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nearlink.messenger.R
import com.nearlink.messenger.ui.components.SafetyNumberView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyNumberScreen(
    viewModel: PairViewModel,
    onBack: () -> Unit,
    onConfirmed: (convId: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val confirmVm: SafetyConfirmViewModel = androidx.hilt.navigation.compose.hiltViewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pair_safety_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.pair_safety_hint), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            state.safetyNumber?.let { SafetyNumberView(text = it) }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.pair_cancel)) }
                Button(onClick = {
                    val peerId = state.pairedPeerDeviceId ?: return@Button
                    confirmVm.confirm(peerId) { convId -> onConfirmed(convId) }
                }) { Text(stringResource(R.string.pair_confirm)) }
            }
        }
    }
}
