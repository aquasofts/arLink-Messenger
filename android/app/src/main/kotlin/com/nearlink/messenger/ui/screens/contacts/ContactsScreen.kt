package com.nearlink.messenger.ui.screens.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nearlink.messenger.core.model.AggregatedPresence
import com.nearlink.messenger.ui.components.ContactRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onPair: () -> Unit,
) {
    val contacts by viewModel.contacts.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("联系人") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onPair) { Icon(Icons.Default.Add, contentDescription = null) }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(contacts, key = { it.deviceId }) { c ->
                ContactRow(
                    contact = c,
                    presence = AggregatedPresence.OFFLINE,         // 真实状态由 PresenceRepository 注入，UI 简化
                    onClick = { onOpenChat(c.deviceId) },
                )
                HorizontalDivider()
            }
        }
    }
}
