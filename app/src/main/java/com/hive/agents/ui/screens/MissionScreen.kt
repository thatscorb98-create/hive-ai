package com.hive.agents.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.data.HiveState
import com.hive.agents.data.MessageKind
import com.hive.agents.data.TaskStatus
import com.hive.agents.ui.BackRow
import com.hive.agents.ui.EmptyState
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.Pill
import com.hive.agents.ui.SectionHeader
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Danger
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.TextDim
import com.hive.agents.ui.timeAgo

@Composable
fun MissionScreen(
    state: HiveState,
    missionId: String?,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val mission = state.missions.firstOrNull { it.id == missionId }

    if (mission == null) {
        Column(Modifier.fillMaxSize()) {
            BackRow("Missions", onBack)
            EmptyState("That mission is gone.")
        }
        return
    }

    val tasks = state.tasks.filter { it.missionId == mission.id }
    val messages = state.messages.filter { it.missionId == mission.id }.sortedBy { it.createdAt }
    val deliverables = state.deliverables.filter { it.missionId == mission.id }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { BackRow("Missions", onBack) }

        item {
            Text(mission.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(mission.brief, fontSize = 13.sp, color = TextDim)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onStart(mission.id) }) { Text("Run mission") }
                TextButton(onClick = { onDelete(mission.id) }) { Text("Delete") }
            }
        }

        item { SectionHeader("TASKS", "${tasks.count { it.status == TaskStatus.DONE }}/${tasks.size} done") }

        if (tasks.isEmpty()) {
            item { EmptyState("Tasks appear once the orchestrator has planned the work.") }
        }

        items(tasks) { task ->
            val agent = state.agent(task.assigneeId)
            val color = when (task.status) {
                TaskStatus.DONE -> Good
                TaskStatus.FAILED -> Danger
                TaskStatus.RUNNING -> Amber
                else -> TextDim
            }
            HiveCard(accent = color) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(task.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Pill(task.status.name.lowercase(), color)
                }
                Text(agent?.name ?: "unassigned", fontSize = 11.sp, color = TextDim)
                if (task.output.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(task.output.take(500), fontSize = 12.sp)
                }
            }
        }

        item { SectionHeader("TIMELINE", "everything the team said") }

        if (messages.isEmpty()) {
            item { EmptyState("The team has not started yet.") }
        }

        items(messages) { message ->
            val speaker = if (message.fromUser) "You" else state.agent(message.agentId)?.name ?: "System"
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
                        if (message.kind == MessageKind.DM) "$speaker -> ${state.agent(message.toAgentId)?.name ?: "?"}" else speaker,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(timeAgo(message.createdAt), fontSize = 10.sp, color = TextDim)
                }
                Spacer(Modifier.height(3.dp))
                Text(message.body, fontSize = 13.sp)
            }
        }

        if (deliverables.isNotEmpty()) {
            item { SectionHeader("DELIVERABLES", "${deliverables.size} produced") }
            items(deliverables) { item ->
                HiveCard(accent = Good) {
                    Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(item.kind.name.lowercase(), fontSize = 10.sp, color = TextDim)
                    if (item.body.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(item.body.take(600), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
