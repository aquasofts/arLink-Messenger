package com.nearlink.messenger.ui.screens.pair

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(
    viewModel: PairViewModel,
    onBack: () -> Unit,
    onPaired: (peerDeviceId: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.startAdvertising() }
    LaunchedEffect(state.pairedPeerDeviceId) {
        state.pairedPeerDeviceId?.let { onPaired(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("附近设备") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            Text("正在广播本机身份，并扫描附近 NearLink 设备…", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (state.error != null) {
                Text("错误：${state.error}", color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.nearby, key = { it.address }) { d ->
                    ListItem(
                        headlineContent = { Text(d.address) },
                        supportingContent = { Text("RSSI ${d.rssi} dBm") },
                        modifier = Modifier.clickable {
                            viewModel.pairWith(d.address, myNickname = null)
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
