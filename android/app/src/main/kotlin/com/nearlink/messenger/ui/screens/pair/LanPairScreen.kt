package com.nearlink.messenger.ui.screens.pair

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanPairScreen(
    viewModel: LanPairViewModel,
    onBack: () -> Unit,
    onPaired: (peerDeviceId: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.pairedPeerDeviceId) {
        state.pairedPeerDeviceId?.let(onPaired)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("局域网设备") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("请让两台设备连接到同一 Wi-Fi 或同一手机热点，并同时打开此页面。", style = MaterialTheme.typography.bodyMedium)
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text("错误：$it", color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.peers, key = { it.deviceId }) { peer ->
                    ListItem(
                        headlineContent = { Text(peer.nickname ?: peer.deviceId.take(8)) },
                        supportingContent = { Text("${peer.host}:${peer.port} · ${peer.deviceId.take(12)}") },
                        modifier = Modifier.clickable { viewModel.pair(peer) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
