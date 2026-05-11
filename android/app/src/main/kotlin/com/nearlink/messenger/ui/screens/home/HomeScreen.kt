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
) {
    val conversations by viewModel.conversations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NearLink") },
                actions = {
                    IconButton(onClick = onOpenContacts) { Icon(Icons.Default.Person, contentDescription = null) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = null) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenPair) { Icon(Icons.Default.Add, contentDescription = "新增联系人") }
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("还没有聊天", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("点 + 通过蓝牙添加附近的好友", color = MaterialTheme.colorScheme.outline)
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
