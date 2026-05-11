package com.nearlink.messenger.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenChat: (String) -> Unit,
    onOpenContacts: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPair: () -> Unit,
    onOpenQrContact: () -> Unit,
    onOpenHotspotPair: () -> Unit,
) {
    val conversations by viewModel.conversations.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("添加联系人", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("通过二维码添加") },
                    supportingContent = { Text("扫描或展示联系人二维码，交换密钥后确认安全码") },
                    modifier = Modifier.clickable {
                        showAddSheet = false
                        onOpenQrContact()
                    },
                )
                ListItem(
                    headlineContent = { Text("通过热点 / 局域网添加") },
                    supportingContent = { Text("连接同一 Wi‑Fi 或热点后通过局域网发现附近设备") },
                    modifier = Modifier.clickable {
                        showAddSheet = false
                        onOpenHotspotPair()
                    },
                )
                ListItem(
                    headlineContent = { Text("通过蓝牙添加") },
                    supportingContent = { Text("使用 BLE 扫描与蓝牙连接添加附近设备") },
                    modifier = Modifier.clickable {
                        showAddSheet = false
                        onOpenPair()
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NearLink") },
                actions = {
                    IconButton(onClick = onOpenContacts) { Icon(Icons.Default.Person, contentDescription = "contacts") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) { Icon(Icons.Default.Add, contentDescription = "新增联系人") }
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("还没有聊天", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("点 + 选择二维码、热点/局域网或蓝牙添加好友", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(conversations, key = { it.convId }) { conv ->
                    ListItem(
                        headlineContent = { Text(conv.title) },
                        supportingContent = { Text(conv.lastMessagePreview ?: "", maxLines = 1) },
                        trailingContent = {
                            if (conv.unreadCount > 0) Badge { Text(conv.unreadCount.toString()) }
                        },
                        modifier = Modifier.clickable { onOpenChat(conv.convId) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
