package com.nearlink.messenger.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * 把 60 位数字按 5 位 12 组排版，便于人眼核对。
 */
@Composable
fun SafetyNumberView(text: String, modifier: Modifier = Modifier) {
    val groups = text.split(' ')
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth().padding(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (rowStart in groups.indices step 4) {
                val rowGroups = groups.subList(rowStart, minOf(rowStart + 4, groups.size))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowGroups.forEach { g ->
                        Text(
                            g,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
