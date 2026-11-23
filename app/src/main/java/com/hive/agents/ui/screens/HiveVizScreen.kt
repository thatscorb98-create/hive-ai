package com.hive.agents.ui.screens

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.data.HiveState
import com.hive.agents.data.MessageKind
import com.hive.agents.ui.BackRow
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.SectionHeader
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.Teal
import com.hive.agents.ui.theme.TextDim
import kotlin.math.cos
import kotlin.math.sin

/** A live graph of the team: bots orbit the orchestrator, edges light up on messages. */
@Composable
fun HiveVizScreen(state: HiveState, onBack: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "hive-orbit")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val agents = state.agents
    val recentDm = state.messages.lastOrNull { it.kind == MessageKind.DM || it.kind == MessageKind.HANDOFF }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { BackRow("Home", onBack) }

        item {
            Text("Hive view", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Who is on the team, and who is talking to whom.", fontSize = 11.sp, color = TextDim)
        }

        item {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .padding(vertical = 8.dp)
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 40.dp.toPx()
                val nodeRadius = 16.dp.toPx()
                val paint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 11.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }

                drawCircle(color = Amber.copy(alpha = 0.18f), radius = 46.dp.toPx(), center = center)
                drawCircle(color = Amber, radius = 20.dp.toPx(), center = center)
                drawContext.canvas.nativeCanvas.drawText(
                    state.orchestrator?.name ?: "Chief",
                    center.x,
                    center.y + 4.dp.toPx(),
                    paint
                )

                if (agents.isEmpty()) return@Canvas

                agents.forEachIndexed { index, agent ->
                    val theta = Math.toRadians(((360.0 / agents.size) * index) + angle)
                    val point = Offset(
                        center.x + (radius * cos(theta)).toFloat(),
                        center.y + (radius * sin(theta)).toFloat()
                    )

                    val active = recentDm != null && (recentDm.agentId == agent.id || recentDm.toAgentId == agent.id)
                    drawLine(
                        color = if (active) Good else Teal.copy(alpha = 0.35f),
                        start = center,
                        end = point,
                        strokeWidth = if (active) 3f else 1.5f
                    )
                    drawCircle(
                        color = if (agent.primary) Amber else Teal,
                        radius = nodeRadius,
                        center = point
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        agent.name.take(9),
                        point.x,
                        point.y + nodeRadius + 12.dp.toPx(),
                        paint
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Stat("bots", agents.size.toString())
                Stat("missions", state.missions.size.toString())
                Stat("messages", state.messages.size.toString())
            }
        }

        item { SectionHeader("LAST COORDINATION", recentDm?.let { "a bot just reached out" } ?: "quiet") }

        if (recentDm != null) {
            item {
                HiveCard(accent = Good) {
                    Text(
                        "${state.agent(recentDm.agentId)?.name ?: "?"} -> ${state.agent(recentDm.toAgentId)?.name ?: "team"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(recentDm.body.take(300), fontSize = 12.sp, color = TextDim)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Amber)
        Text(label, fontSize = 10.sp, color = TextDim)
    }
}
