package com.nearlink.messenger.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nearlink.messenger.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val nickname by viewModel.nickname.collectAsState()
    var nicknameInput by rememberSaveable(nickname) { mutableStateOf(nickname ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
                },
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_device_id)) },
                supportingContent = { Text(state.deviceId, fontFamily = FontFamily.Monospace) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_fingerprint)) },
                supportingContent = { Text(state.fingerprint, fontFamily = FontFamily.Monospace) },
            )
            HorizontalDivider()
            OutlinedTextField(
                value = nicknameInput,
                onValueChange = { nicknameInput = it },
                label = { Text(stringResource(R.string.profile_nickname)) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.setNickname(nicknameInput) }) { Text("保存") }
        }
    }
}
