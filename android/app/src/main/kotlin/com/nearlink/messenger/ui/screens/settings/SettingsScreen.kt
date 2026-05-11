package com.nearlink.messenger.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nearlink.messenger.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val dbEnc by viewModel.dbEncryption.collectAsState()
    var input by rememberSaveable(serverUrl) { mutableStateOf(serverUrl ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.settings_server_url)) },
                placeholder = { Text(stringResource(R.string.settings_server_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.saveServerUrl(input) }) {
                Text(stringResource(R.string.settings_save))
            }
            HorizontalDivider(Modifier.padding(vertical = 24.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_db_encryption)) },
                trailingContent = {
                    Switch(checked = dbEnc, onCheckedChange = { viewModel.setDbEncryption(it) })
                },
                supportingContent = { Text("启用后将使用 SQLCipher 对本地数据库加密；首次启用需要设置口令。") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                supportingContent = { Text("NearLink Messenger 0.1.0 · 端到端加密 · 蓝牙近场直传") },
            )
        }
    }
}
