package com.hive.agents.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.data.HiveState
import com.hive.agents.data.MessageKind
import com.hive.agents.data.ProspectStage
import com.hive.agents.data.TaskStatus
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.Pill
import com.hive.agents.ui.SectionHeader
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Danger
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.PanelHigh
import com.hive.agents.ui.theme.Teal
import com.hive.agents.ui.theme.TextDim
import com.hive.agents.ui.timeAgo

@Composable
fun HomeScreen(
    state: HiveState,
    autopilot: Boolean,
    busy: Boolean,
    onCommand: (String) -> Unit,
    onRunShift: () -> Unit,
    onOpenViz: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenBrowsers: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenMissions: () -> Unit,
    onOpenMission: (String) -> Unit
) {
    var command by remember { mutableStateOf("") }

    val won = state.prospects.filter { it.stage == ProspectStage.WON }
    val revenueCents = won.sumOf { it.valueCents }
    val pipelineCents = state.prospects
        .filter { it.stage != ProspectStage.WON && it.stage != ProspectStage.LOST }
        .sumOf { it.valueCents }
    val doneTasks = state.tasks.count { it.status == TaskStatus.DONE }
    val automation = if (state.tasks.isEmpty()) 0 else doneTasks * 100 / state.tasks.size
    val activity = state.messages.sortedByDescending { it.createdAt }.take(6)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(8.dp).height(8.dp).background(if (autopilot) Good else TextDim))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (autopilot) "All systems online" else "Autopilot is off",
                    fontSize = 11.sp,
                    color = if (autopilot) Good else TextDim
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenViz) { Text("3D", fontSize = 12.sp) }
            }
        }

        item {
            HiveCard(accent = Amber) {
                Text("Your business is running", fontSize = 13.sp, color = TextDim)
                Text(
                    if (autopilot) "autonomously." else "when you run a shift.",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text("Today's booked revenue", fontSize = 11.sp, color = TextDim)
                Text(
                    "$${"%.2f".format(revenueCents / 100.0)}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Good
                )
                Text(
                    "Pipeline ${"%.2f".format(pipelineCents / 100.0)} across ${state.prospects.size} leads",
                    fontSize = 11.sp,
                    color = TextDim
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRunShift) {
                        Text(if (busy) "Working..." else "Run a shift")
                    }
                    TextButton(onClick = onOpenStudio) { Text("Studio", fontSize = 12.sp) }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricTile("Automation", "$automation%", Good, Modifier.weight(1f))
                MetricTile("Bots", "${state.agents.size}", Teal, Modifier.weight(1f))
                MetricTile("Products", "${state.products.size}", Amber, Modifier.weight(1f))
            }
        }

        item { SectionHeader("AGENT NETWORK", "${state.agents.size} bots working together") }

        if (state.agents.isEmpty()) {
            item { Text("No bots yet.", color = TextDim, fontSize = 12.sp) }
        }

        items(state.agents) { agent ->
            val last = state.messages.lastOrNull { it.agentId == agent.id }
            val status = when {
                busy -> "working..."
                last == null -> "ready"
                System.currentTimeMillis() - last.createdAt < 600_000 -> last.body.take(48)
                else -> "idle"
            }
            HiveCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(20.dp).height(20.dp).clip(RoundedCornerShape(50))
                        .background(if (agent.primary) Amber else Teal))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(agent.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${agent.role} - $status", fontSize = 11.sp, color = TextDim)
                    }
                    Pill(if (agent.active) "active" else "idle", Good)
                }
            }
        }

        item { SectionHeader("LIVE ACTIVITY", "what the team is doing") }

        if (activity.isEmpty()) {
            item { Text("Quiet so far. Give the team a command below.", color = TextDim, fontSize = 12.sp) }
        }

        items(activity) { message ->
            val name = if (message.fromUser) "You" else state.agent(message.agentId)?.name ?: "System"
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(6.dp).height(6.dp).clip(RoundedCornerShape(50)).background(
                    when (message.kind) {
                        MessageKind.DM -> Amber
                        MessageKind.HANDOFF -> Good
                        else -> Teal
                    }
                ))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "$name: ${message.body.take(90)}",
                        fontSize = 12.sp
                    )
                }
                Text(timeAgo(message.createdAt), fontSize = 10.sp, color = TextDim)
            }
        }

        item { SectionHeader("QUICK ACTIONS", "") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onOpenBrowsers) { Text("Browsers", fontSize = 12.sp) }
                TextButton(onClick = onOpenInbox) { Text("Email", fontSize = 12.sp) }
                TextButton(onClick = onOpenStudio) { Text("Products", fontSize = 12.sp) }
                TextButton(onClick = onOpenMissions) { Text("Missions", fontSize = 12.sp) }
            }
        }

        item { SectionHeader("MISSIONS", "") }
        items(state.missions.sortedByDescending { it.createdAt }.take(4)) { mission ->
            HiveCard(onClick = { onOpenMission(mission.id) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(mission.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Pill(
                        mission.status.name.lowercase(),
                        if (mission.status.name == "DONE") Good else if (mission.status.name == "BLOCKED") Danger else Amber
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    singleLine = true,
                    placeholder = { Text("Give the team a command...", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (command.isNotBlank()) {
                        onCommand(command)
                        command = ""
                    }
                }) { Text("Send") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PanelHigh)
            .padding(10.dp)
    ) {
        Column {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = TextDim)
        }
    }
}
