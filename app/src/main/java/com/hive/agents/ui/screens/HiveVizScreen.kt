package com.hive.agents.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.agent.ModelRouter
import com.hive.agents.data.Agent
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

private data class StationRoom(
    val key: String,
    val title: String,
    val color: Color,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
)

private val stationRooms = listOf(
    StationRoom("COMMAND", "COMMAND", Amber, 0.30f, 0.035f, 0.40f, 0.145f),
    StationRoom("RESEARCH", "RESEARCH", Teal, 0.035f, 0.285f, 0.42f, 0.145f),
    StationRoom("SALES", "SALES", Color(0xFF72E0A0), 0.545f, 0.285f, 0.42f, 0.145f),
    StationRoom("STUDIO", "STUDIO", Color(0xFFC084FC), 0.035f, 0.485f, 0.42f, 0.145f),
    StationRoom("MAKER", "MAKER", Color(0xFF7C9CFF), 0.545f, 0.485f, 0.42f, 0.145f),
    StationRoom("SHOWCASE", "SHOWCASE", Color(0xFFFF8FA3), 0.035f, 0.685f, 0.42f, 0.145f),
    StationRoom("QA", "PROOFING", Color(0xFFFFB86B), 0.545f, 0.685f, 0.42f, 0.145f)
)

private fun roomKeyFor(role: String): String {
    val r = role.lowercase()
    return when {
        r.contains("founder") || r.contains("chief") || r.contains("orchestr") -> "COMMAND"
        r.contains("research") || r.contains("lead gen") -> "RESEARCH"
        r.contains("sales") || r.contains("closer") -> "SALES"
        r.contains("market") || r.contains("content") || r.contains("spark") -> "STUDIO"
        r.contains("product") || r.contains("design") || r.contains("maker") -> "MAKER"
        r.contains("showcase") || r.contains("demo") -> "SHOWCASE"
        r.contains("proof") -> "QA"
        else -> "COMMAND"
    }
}

/**
 * The station is the runtime, StarNet-style. Rooms are capability-scoped departments, hallways are
 * handoff lanes, and every moving dot is a real message. A bot's room follows its role; its ring
 * pulses while a task is running. Tap a room to isolate it.
 */
@Composable
fun HiveVizScreen(state: HiveState, onBack: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "hive-station")
    val packet by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "packet"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val agents = state.agents
    val running = state.tasks.filter { it.status == TaskStatus.RUNNING }
    val recent = state.messages.sortedByDescending { it.createdAt }.take(8)
    val commandRoom = stationRooms.first()
    var focusRoom by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item { BackRow("Home", onBack) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Hive Station", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "A cutaway of the company. Rooms are teams, hallways are handoffs.",
                        fontSize = 10.sp,
                        color = TextDim
                    )
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(
                        if (running.isEmpty()) "STANDBY" else "LIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (running.isEmpty()) TextDim else Good
                    )
                    Text("${agents.size} crew", fontSize = 9.sp, color = TextDim)
                }
            }
        }

        item {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(510.dp)
                    .padding(vertical = 6.dp)
                    .pointerInput(agents) {
                        detectTapGestures { offset ->
                            val nx = offset.x / size.width
                            val ny = offset.y / size.height
                            val hit = stationRooms.firstOrNull { room ->
                                nx >= room.x && nx <= room.x + room.w &&
                                    ny >= room.y && ny <= room.y + room.h
                            }
                            focusRoom = if (hit?.key == focusRoom) null else hit?.key
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val depth = 7.dp.toPx()
                val paint = Paint().apply {
                    isAntiAlias = true
                    typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                }

                // Deep-space background and a faint isometric floor.
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFF05070D), Color(0xFF090E17), Color(0xFF05070C))
                    )
                )
                repeat(76) { i ->
                    val x = ((i * 37 + 11) % 101) / 100f * w
                    val y = ((i * 61 + 7) % 72) / 100f * h * 0.78f
                    drawCircle(Color.White.copy(alpha = if (i % 7 == 0) 0.42f else 0.15f), 0.8.dp.toPx(), Offset(x, y))
                }
                val horizon = h * 0.24f
                repeat(9) { i ->
                    val y = horizon + (h - horizon) * (i / 8f)
                    drawLine(Color(0xFF4FD1C5).copy(alpha = 0.045f), Offset(0f, y), Offset(w, y), 1f)
                }
                repeat(9) { i ->
                    val xBottom = w * (i / 8f)
                    drawLine(
                        Color(0xFF4FD1C5).copy(alpha = 0.04f),
                        Offset(w / 2f, horizon),
                        Offset(xBottom, h),
                        1f
                    )
                }

                // Hallways first, so rooms sit on top of their lanes.
                val command = stationRooms.first()
                val commandBottom = Offset((command.x + command.w / 2f) * w, (command.y + command.h) * h)
                stationRooms.drop(1).forEach { room ->
                    val laneEnd = Offset((room.x + room.w / 2f) * w, room.y * h)
                    drawLine(room.color.copy(alpha = 0.08f), commandBottom, laneEnd, 7.dp.toPx())
                    drawLine(room.color.copy(alpha = 0.24f), commandBottom, laneEnd, 1.2.dp.toPx())
                }

                // Room boxes.
                stationRooms.forEach { room ->
                    val x = room.x * w
                    val y = room.y * h
                    val rw = room.w * w
                    val rh = room.h * h
                    val focused = focusRoom == null || focusRoom == room.key
                    val roomAgents = if (room.key == "COMMAND") {
                        agents.filter { it.primary } + agents.filter { !it.primary && roomKeyFor(it.role) == "COMMAND" }
                    } else {
                        agents.filter { !it.primary && roomKeyFor(it.role) == room.key }
                    }
                    val roomRunning = running.filter { task -> roomAgents.any { it.id == task.assigneeId } }
                    val active = roomRunning.isNotEmpty()
                    val alpha = if (focused) 1f else 0.33f

                    // 3D lid.
                    val top = Path().apply {
                        moveTo(x, y + depth)
                        lineTo(x + depth, y)
                        lineTo(x + rw + depth, y)
                        lineTo(x + rw, y + depth)
                        close()
                    }
                    drawPath(top, room.color.copy(alpha = 0.20f * alpha))
                    // Front face.
                    drawRoundRect(
                        color = Color(0xFF0C121B).copy(alpha = alpha),
                        topLeft = Offset(x, y + depth),
                        size = Size(rw, rh),
                        cornerRadius = CornerRadius(5.dp.toPx())
                    )
                    drawRoundRect(
                        color = room.color.copy(alpha = (if (active) 0.78f else 0.42f) * alpha),
                        topLeft = Offset(x, y + depth),
                        size = Size(rw, rh),
                        cornerRadius = CornerRadius(5.dp.toPx()),
                        style = Stroke(width = (if (active) 2f else 1.1f).dp.toPx())
                    )
                    if (active) {
                        drawRoundRect(
                            color = room.color.copy(alpha = (0.10f + 0.12f * pulse) * alpha),
                            topLeft = Offset(x - 2.dp.toPx(), y + depth - 2.dp.toPx()),
                            size = Size(rw + 4.dp.toPx(), rh + 4.dp.toPx()),
                            cornerRadius = CornerRadius(7.dp.toPx()),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                    if (focusRoom == room.key) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.8f),
                            topLeft = Offset(x - 2.dp.toPx(), y + depth - 2.dp.toPx()),
                            size = Size(rw + 4.dp.toPx(), rh + 4.dp.toPx()),
                            cornerRadius = CornerRadius(7.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    // Workstation desks: tiny pixel consoles along the room's back wall.
                    repeat(3) { i ->
                        val dx = x + rw * 0.58f + i * 13.dp.toPx()
                        val dy = y + depth + 22.dp.toPx()
                        drawRoundRect(
                            color = room.color.copy(alpha = 0.16f * alpha),
                            topLeft = Offset(dx, dy),
                            size = Size(9.dp.toPx(), 6.dp.toPx()),
                            cornerRadius = CornerRadius(1.dp.toPx())
                        )
                        drawRoundRect(
                            color = room.color.copy(alpha = 0.55f * alpha),
                            topLeft = Offset(dx + 1.5.dp.toPx(), dy - 3.dp.toPx()),
                            size = Size(6.dp.toPx(), 3.dp.toPx()),
                            cornerRadius = CornerRadius(1.dp.toPx())
                        )
                    }

                    paint.color = room.color.copy(alpha = alpha).toArgb()
                    paint.textSize = 9.sp.toPx()
                    paint.textAlign = Paint.Align.LEFT
                    drawContext.canvas.nativeCanvas.drawText(
                        room.title,
                        x + 8.dp.toPx(),
                        y + depth + 13.dp.toPx(),
                        paint
                    )

                    paint.color = TextDim.copy(alpha = alpha).toArgb()
                    paint.textSize = 7.sp.toPx()
                    val subtitle = when {
                        room.key == "COMMAND" && roomAgents.isEmpty() -> "waiting for Chief"
                        roomAgents.isEmpty() -> "no crew yet"
                        roomRunning.isNotEmpty() -> roomRunning.first().title.take(24)
                        else -> "${roomAgents.size} crew - ready"
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        subtitle,
                        x + 8.dp.toPx(),
                        y + depth + 24.dp.toPx(),
                        paint
                    )

                    // Bots at their workstations. Max four visible per room, then a count.
                    val cols = 2
                    val visible = roomAgents.take(4)
                    visible.forEachIndexed { index, agent ->
                        val slotW = rw / cols
                        val slotH = (rh - 38.dp.toPx()) / 2f
                        val px = x + slotW * (index % cols) + slotW / 2f
                        val py = y + depth + 36.dp.toPx() + slotH * (index / cols)
                        val botActive = running.any { it.assigneeId == agent.id }
                        val botColor = room.color

                        drawCircle(botColor.copy(alpha = 0.10f + 0.12f * pulse * alpha), 15.dp.toPx(), Offset(px, py))
                        if (botActive) {
                            drawCircle(
                                botColor.copy(alpha = 0.55f * alpha),
                                17.dp.toPx(),
                                Offset(px, py),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                        // Head + body, deliberately pixel-ish.
                        drawCircle(botColor.copy(alpha = alpha), 4.dp.toPx(), Offset(px, py - 5.dp.toPx()))
                        drawRoundRect(
                            color = botColor.copy(alpha = 0.72f * alpha),
                            topLeft = Offset(px - 5.dp.toPx(), py - 1.dp.toPx()),
                            size = Size(10.dp.toPx(), 9.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )
                        paint.color = Color.White.copy(alpha = alpha).toArgb()
                        paint.textSize = 7.sp.toPx()
                        paint.textAlign = Paint.Align.CENTER
                        drawContext.canvas.nativeCanvas.drawText(
                            agent.name.take(8),
                            px,
                            py + 14.dp.toPx(),
                            paint
                        )
                    }
                    if (roomAgents.size > 4) {
                        paint.color = room.color.copy(alpha = alpha).toArgb()
                        paint.textAlign = Paint.Align.RIGHT
                        paint.textSize = 7.sp.toPx()
                        drawContext.canvas.nativeCanvas.drawText(
                            "+${roomAgents.size - 4}",
                            x + rw - 6.dp.toPx(),
                            y + depth + rh - 6.dp.toPx(),
                            paint
                        )
                    }
                }

                // Message packets: command/agent room to command/agent room.
                val positions = mutableMapOf<String, Offset>()
                agents.forEach { agent ->
                    val room = if (agent.primary) command else stationRooms.firstOrNull { it.key == roomKeyFor(agent.role) } ?: command
                    positions[agent.id] = Offset(
                        (room.x + room.w / 2f) * w,
                        (room.y + room.h * 0.72f) * h
                    )
                }
                val commandCenter = Offset((command.x + command.w / 2f) * w, (command.y + command.h * 0.70f) * h)
                state.messages.takeLast(14).forEachIndexed { index, message ->
                    val from = if (message.fromUser) commandCenter else positions[message.agentId] ?: commandCenter
                    val to = positions[message.toAgentId] ?: commandCenter
                    if (from == to) return@forEachIndexed
                    val progress = (packet + index * 0.19f) % 1f
                    val point = Offset(
                        from.x + (to.x - from.x) * progress,
                        from.y + (to.y - from.y) * progress
                    )
                    val color = when (message.kind) {
                        MessageKind.DM -> Amber
                        MessageKind.HANDOFF -> Good
                        else -> Teal
                    }
                    drawCircle(color.copy(alpha = 0.18f), 8.dp.toPx(), point)
                    drawCircle(color, 3.dp.toPx(), point)
                }
            }
        }

        item {
            Text(
                "Tap a room to isolate it. Hallways carry handoffs; each dot is a message in flight.",
                fontSize = 9.sp,
                color = TextDim,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Stat("rooms", "7")
                Stat("crew", agents.size.toString())
                Stat("running", running.size.toString())
                Stat("sales", state.salesMade.toString())
            }
        }

        item { SectionHeader("LIVE FLOW", "real messages between bots") }

        if (recent.isEmpty()) {
            item { EmptyState("Quiet. The station has not said anything yet.") }
        }

        items(recent) { message ->
            val from = if (message.fromUser) "You" else state.agent(message.agentId)?.name ?: "System"
            val to = message.toAgentId.takeIf { it.isNotBlank() }?.let { state.agent(it)?.name }
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
                    Text(timeAgo(message.createdAt), fontSize = 9.sp, color = TextDim)
                }
                Spacer(Modifier.height(3.dp))
                Text(message.body.take(180), fontSize = 11.sp)
            }
        }

        item {
            SectionHeader(
                "CREW + WORK",
                if (running.isEmpty()) "all quiet" else "${running.size} task(s) in flight"
            )
        }

        items(agents) { agent ->
            val room = stationRooms.firstOrNull { it.key == roomKeyFor(agent.role) } ?: commandRoom
            val mine = running.filter { it.assigneeId == agent.id }
            val queued = state.tasks.count { it.assigneeId == agent.id && it.status == TaskStatus.QUEUED }
            val last = state.messages.lastOrNull { it.agentId == agent.id || it.toAgentId == agent.id }
            HiveCard(
                accent = when {
                    mine.isNotEmpty() -> Good
                    agent.primary -> Amber
                    else -> room.color
                }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { focusRoom = if (focusRoom == room.key) null else room.key }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(agent.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(room.title, fontSize = 8.sp, color = room.color)
                    }
                    Text(
                        "${agent.role} - ${ModelRouter.label(agent, state.settings)}",
                        fontSize = 9.sp,
                        color = TextDim
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when {
                            mine.isNotEmpty() -> "working: ${mine.first().title}"
                            queued > 0 -> "$queued queued"
                            else -> "ready in ${room.title.lowercase()}"
                        },
                        fontSize = 11.sp,
                        color = if (mine.isNotEmpty()) Good else TextDim
                    )
                    if (last != null) {
                        Text(
                            (if (last.agentId == agent.id) "said: " else "heard: ") + last.body.take(80),
                            fontSize = 9.sp,
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
        Text(label, fontSize = 9.sp, color = TextDim)
    }
}
