package com.nearlink.messenger.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nearlink.messenger.core.model.Message
import com.nearlink.messenger.core.model.MessageStatus

@Composable
fun MessageBubble(message: Message, modifier: Modifier = Modifier) {
    val isOut = message.isOutgoing
    val bg = if (isOut) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isOut) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.revoked) {
                    Text("已撤回", color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(message.text ?: "[未知消息]", color = fg, style = MaterialTheme.typography.bodyLarge)
                    if (message.edited) {
                        Text("已编辑", color = fg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (isOut) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statusLabel(message.status),
                        color = fg.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun statusLabel(s: MessageStatus): String = when (s) {
    MessageStatus.PENDING -> "…"
    MessageStatus.SENDING -> "发送中"
    MessageStatus.SENT -> "已发送"
    MessageStatus.DELIVERED -> "已送达"
    MessageStatus.READ -> "已读"
    MessageStatus.FAILED -> "发送失败"
}
