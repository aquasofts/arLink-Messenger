package com.nearlink.messenger.ui.screens.qr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nearlink.messenger.ui.components.QrCodeImage
import com.nearlink.messenger.ui.components.QrScanButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrMessageScreen(
    viewModel: QrMessageViewModel,
    mode: QrMessageMode,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == QrMessageMode.SHOW) "二维码发送信息" else "二维码获取信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (mode == QrMessageMode.SHOW) {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = viewModel::setText,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("要通过二维码发送的消息") },
                    maxLines = 4,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::generate) { Text("生成加密二维码内容") }
                if (state.payload.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    QrCodeImage(state.payload, Modifier.size(220.dp))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.payload,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                )
            } else {
                QrScanButton { viewModel.setImportText(it) }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.importText,
                    onValueChange = viewModel::setImportText,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                    label = { Text("粘贴收到的二维码消息内容") },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::importMessage) { Text("导入并解密") }
            }
            state.status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

enum class QrMessageMode { SHOW, SCAN }
