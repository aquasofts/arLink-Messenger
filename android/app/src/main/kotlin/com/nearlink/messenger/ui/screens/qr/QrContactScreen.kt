package com.nearlink.messenger.ui.screens.qr

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nearlink.messenger.ui.components.QrCodeImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrContactScreen(
    viewModel: QrContactViewModel,
    onBack: () -> Unit,
    onImported: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("联系人二维码") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
                },
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("我的联系人二维码内容", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (state.payload.isNotBlank()) QrCodeImage(state.payload, Modifier.size(220.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.payload,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.height(24.dp))
            Text("粘贴对方联系人二维码内容", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.importText,
                onValueChange = viewModel::setImportText,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.importContact(onImported) }) { Text("导入联系人") }
            state.status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
