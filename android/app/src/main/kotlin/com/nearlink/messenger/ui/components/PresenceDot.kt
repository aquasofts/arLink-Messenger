package com.nearlink.messenger.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nearlink.messenger.core.model.AggregatedPresence
import com.nearlink.messenger.ui.theme.OnlineGreen
import com.nearlink.messenger.ui.theme.WarnAmber

@Composable
fun PresenceDot(state: AggregatedPresence, modifier: Modifier = Modifier) {
    val color: Color = when (state) {
        AggregatedPresence.BT_ONLINE -> OnlineGreen
        AggregatedPresence.SERVER_ONLINE -> WarnAmber
        AggregatedPresence.OFFLINE -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}
