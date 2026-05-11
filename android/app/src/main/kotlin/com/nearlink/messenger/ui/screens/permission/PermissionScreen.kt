package com.nearlink.messenger.ui.screens.permission

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nearlink.messenger.R
import com.nearlink.messenger.core.permissions.BluetoothPermissions
import com.nearlink.messenger.core.permissions.PermissionHelper

@Composable
fun PermissionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(PermissionHelper.allGranted(context, BluetoothPermissions.runtime)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        granted = result.values.all { it }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.perm_bluetooth_rationale), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.perm_notification_rationale), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        Button(onClick = {
            launcher.launch(BluetoothPermissions.runtime + BluetoothPermissions.notification)
        }) { Text(stringResource(R.string.perm_grant)) }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onDone) { Text(stringResource(R.string.perm_skip)) }

        if (granted) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDone) { Text("继续") }
        }
    }
}
