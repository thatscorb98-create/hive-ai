package com.hive.agents.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.data.Agent
import com.hive.agents.ui.theme.Good
import java.util.Locale

private val avatarPalette = listOf(
    Color(0xFFFFC53D),
    Color(0xFF4FD1C5),
    Color(0xFF7C9CFF),
    Color(0xFFC084FC),
    Color(0xFFFF8FA3),
    Color(0xFF72E0A0)
)

fun agentColor(agent: Agent): Color =
    avatarPalette[Math.floorMod(agent.name.hashCode(), avatarPalette.size)]

/**
 * A small, stable blob for each bot. Same color everywhere so the human can learn the crew by
 * sight in the chat rail, dashboard and station.
 */
@Composable
fun AgentAvatar(
    agent: Agent,
    size: Dp = 34.dp,
    selected: Boolean = false,
    showOnline: Boolean = true
) {
    val shape = RoundedCornerShape(size * 0.36f)
    val color = agentColor(agent)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.48f))
                )
            )
            .then(
                if (selected) Modifier.border(2.dp, color, shape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            agent.name.trim().take(1).uppercase(Locale.getDefault()).ifBlank { "?" },
            color = Color(0xFF0B0D10),
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold
        )
        if (showOnline) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.25f)
                    .clip(RoundedCornerShape(50))
                    .background(if (agent.active) Good else Color(0xFF555B66))
                    .border(1.5.dp, Color(0xFF0A0A0B), RoundedCornerShape(50))
            )
        }
    }
}
