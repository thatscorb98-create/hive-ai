package com.hive.agents.ui.screens

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.agent.ModelRouter
import com.hive.agents.data.HiveState
import com.hive.agents.data.MessageKind
import com.hive.agents.data.TaskStatus
import com.hive.agents.ui.BackRow
import com.hive.agents.ui.EmptyState
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.SectionHeader
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.Teal
import com.hive.agents.ui.theme.TextDim
import com.hive.agents.ui.timeAgo
import kotlin.math.cos
import kotlin.math.sin

/**
 * The command deck. A tilted orbit gives the 3D read, the centre is the lead, and each packet
 * travelling an edge is a real message between two bots. Below it, Grok-style: exactly what every
 * bot is running, what is queued, which model it is on, and the last thing it said or heard.
 */
@Composable
fun HiveVizScreen(state: HiveState, onBack: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "hive-deck")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )
    val packet by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "packet"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val agents = state.agents
    val lead = state.orchestrator
    val specialists = agents.filter { it.id != lead?.id }
    val recent = state.messages.sortedByDescending { it.createdAt }.take(10)
    val running = state.tasks.filter { it.status == TaskStatus.RUNNING }
    var focusId by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { BackRow("Team", onBack) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Hive deck", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Live traffic, work in flight, and who owns what.", fontSize = 11.sp, color = TextDim)
                }
                Text(
                    if (running.isEmpty()) "IDLE" else "LIVE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (running.isEmpty()) TextDim else Good
                )
            }
        }

        item {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .padding(vertical = 6.dp)
            ) {
                val center = Offset(size.width / 2f, size.height * 0.50f)
                val xRadius = size.width * 0.37f
                val yRadius = size.height * 0.34f
                val leadRadius = 26.dp.toPx()
                val paint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 10.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }

                // The tilted orbit reads as depth: bots at the back are smaller and dimmer.
                drawOval(
                    color = Teal.copy(alpha = 0.12f),
                    topLeft = Offset(center.x - xRadius, center.y - yRadius),
                    size = Size(xRadius * 2f, yRadius * 2f),
                    style = Stroke(width = 1.5f)
                )
                drawOval(
                    color = Teal.copy(alpha = 0.06f),
                    topLeft = Offset(center.x - xRadius * 0.66f, center.y - yRadius * 0.66f),
                    size = Size(xRadius * 1.32f, yRadius * 1.32f),
                    style = Stroke(width = 1f)
                )

                drawCircle(Amber.copy(alpha = 0.09f + 0.07f * pulse), leadRadius * 2.5f, center)
                drawCircle(Amber.copy(alpha = 0.18f), leadRadius * 1.55f, center)
                drawCircle(Amber, leadRadius, center)
                paint.alpha = 255
                drawContext.canvas.nativeCanvas.drawText(
                    lead?.name ?: "Chief",
                    center.x,
                    center.y + 4.dp.toPx(),
                    paint
                )

                if (specialists.isEmpty()) return@Canvas

                val nodes = specialists.mapIndexed { index, agent ->
                    val theta = Math.toRadians((360.0 / specialists.size * index) + angle)
                    val depth = ((sin(theta) + 1.0) / 2.0).toFloat()
                    val point = Offset(
                        center.x + (xRadius * cos(theta)).toFloat(),
                        center.y + (yRadius * sin(theta)).toFloat()
                    )
                    Triple(agent, point, depth)
                }
                val positions = mutableMapOf<String, Offset>()
                lead?.let { positions[it.id] = center }
                nodes.forEach { positions[it.first.id] = it.second }

                nodes.forEach { (agent, point, depth) ->
                    val active = running.any { it.assigneeId == agent.id }
                    val focused = focusId == null || focusId == agent.id
                    val alpha = if (focused) 0.30f + 0.30f * depth else 0.07f
                    drawLine(
                        color = if (active) Good.copy(alpha = alpha) else Teal.copy(alpha = alpha),
                        start = center,
                        end = point,
                        strokeWidth = 1f + 2f * depth
                    )
                }

                recent.forEachIndexed { index, message ->
                    val from = positions[message.agentId] ?: center
                    val to = positions[message.toAgentId] ?: center
                    if (from == to) return@forEachIndexed
                    if (focusId != null && focusId != message.agentId && focusId != message.toAgentId) {
                        return@forEachIndexed
                    }
                    val progress = ((packet + index * 0.23f) % 1f)
                    val point = Offset(
                        from.x + (to.x - from.x) * progress,
                        from.y + (to.y - from.y) * progress
                    )
                    val color = when (message.kind) {
                        MessageKind.DM -> Amber
                        MessageKind.HANDOFF -> Good
                        else -> Teal
                    }
                    drawCircle(color.copy(alpha = 0.20f), 8.dp.toPx(), point)
                    drawCircle(color, 3.5.dp.toPx(), point)
                }

                nodes.sortedBy { it.third }.forEach { (agent, point, depth) ->
                    val radius = (13f + 8f * depth).dp.toPx()
                    val active = running.any { it.assigneeId == agent.id }
                    if (active) {
                        drawCircle(
                            color = Good.copy(alpha = 0.20f + 0.25f * pulse),
                            radius = radius + 9.dp.toPx(),
                            center = point,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                    if (focusId == agent.id) {
                        drawCircle(
                            color = Amber,
                            radius = radius + 13.dp.toPx(),
                            center = point,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                    drawCircle(Teal.copy(alpha = 0.16f), radius + 6.dp.toPx(), point)
                    drawCircle(if (agent.primary) Amber else Teal, radius, point)
                    paint.alpha = (110 + 145 * depth).toInt()
                    drawContext.canvas.nativeCanvas.drawText(
                        agent.name.take(10),
                        point.x,
                        point.y + radius + 13.dp.toPx(),
                        paint
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("outer ring = bots, dot = message in flight", fontSize = 9.sp, color = TextDim)
                Text("tap a card to trace", fontSize = 9.sp, color = TextDim)
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Stat("bots", agents.size.toString())
                Stat("running", running.size.toString())
                Stat("messages", state.messages.size.toString())
                Stat("sales", state.salesMade.toString())
            }
        }

        item { SectionHeader("LIVE FLOW", "messages moving between bots") }

        if (recent.isEmpty()) {
            item { EmptyState("Quiet. The team has not said anything yet.") }
        }

        items(recent) { message ->
            val from = if (message.fromUser) "You" else state.agent(message.agentId)?.name ?: "System"
            val to = message.toAgentId
                .takeIf { it.isNotBlank() }
                ?.let { state.agent(it)?.name }
            HiveCard(
                accent = when (message.kind) {
                    MessageKind.DM -> Amber
                    MessageKind.HANDOFF -> Good
                    MessageKind.SYSTEM -> TextDim
                    else -> null
                }
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (to != null) "$from -> $to" else from,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(timeAgo(message.createdAt), fontSize = 10.sp, color = TextDim)
                }
                Spacer(Modifier.height(3.dp))
                Text(message.body.take(220), fontSize = 12.sp)
            }
        }

        item {
            SectionHeader(
                "WORK DISTRIBUTION",
                if (running.isEmpty()) "no task is running" else "${running.size} task(s) in flight"
            )
        }

        items(agents) { agent ->
            val mine = running.filter { it.assigneeId == agent.id }
            val queued = state.tasks.count {
                it.assigneeId == agent.id && it.status == TaskStatus.QUEUED
            }
            val last = state.messages.lastOrNull {
                it.agentId == agent.id || it.toAgentId == agent.id
            }
            HiveCard(
                accent = when {
                    mine.isNotEmpty() -> Good
                    agent.primary -> Amber
                    else -> Teal
                }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { focusId = if (focusId == agent.id) null else agent.id }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(agent.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            ModelRouter.label(agent, state.settings),
                            fontSize = 9.sp,
                            color = TextDim
                        )
                    }
                    Text(
                        agent.role + if (agent.primary) " - lead" else "",
                        fontSize = 10.sp,
                        color = TextDim
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when {
                            mine.isNotEmpty() -> "working: ${mine.first().title}"
                            queued > 0 -> "$queued task(s) queued"
                            else -> "idle, waiting for the next shift"
                        },
                        fontSize = 11.sp,
                        color = if (mine.isNotEmpty()) Good else TextDim
                    )
                    if (last != null) {
                        Text(
                            (if (last.agentId == agent.id) "said: " else "heard: ") +
                                last.body.take(90),
                            fontSize = 10.sp,
                            color = TextDim
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Amber)
        Text(label, fontSize = 10.sp, color = TextDim)
    }
}
