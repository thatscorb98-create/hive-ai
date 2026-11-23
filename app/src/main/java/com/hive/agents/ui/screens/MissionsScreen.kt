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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.data.HiveState
import com.hive.agents.data.MissionStatus
import com.hive.agents.ui.EmptyState
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.LabeledField
import com.hive.agents.ui.Pill
import com.hive.agents.ui.SectionHeader
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Danger
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.TextDim
import com.hive.agents.ui.timeAgo

@Composable
fun MissionsScreen(
    state: HiveState,
    onCreate: (String, String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenViz: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenBrowsers: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var brief by remember { mutableStateOf("") }
    var composing by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(onClick = onOpenViz) { Text("3D view", fontSize = 12.sp) }
                TextButton(onClick = onOpenStudio) { Text("Studio", fontSize = 12.sp) }
                TextButton(onClick = onOpenInbox) { Text("Email", fontSize = 12.sp) }
                TextButton(onClick = onOpenBrowsers) { Text("Browsers", fontSize = 12.sp) }
            }
        }

        item {
            SectionHeader("NEW MISSION", "Give the team a job with a clear finish line.")
            if (!composing) {
                Button(onClick = { composing = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Plan a mission")
                }
            } else {
                Column {
                    LabeledField("What needs doing", title) { title = it }
                    Spacer(Modifier.height(6.dp))
                    LabeledField("Brief", brief, singleLine = false) { brief = it }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            onCreate(title, brief)
                            title = ""
                            brief = ""
                            composing = false
                        }) { Text("Create") }
                        TextButton(onClick = { composing = false }) { Text("Cancel") }
                    }
                }
            }
        }

        item { SectionHeader("MISSIONS", "${state.missions.size} total") }

        if (state.missions.isEmpty()) {
            item { EmptyState("No missions yet. Create one and the orchestrator will hire the team it needs.") }
        }

        items(state.missions.sortedByDescending { it.createdAt }) { mission ->
            val statusColor = when (mission.status) {
                MissionStatus.DONE -> Good
                MissionStatus.BLOCKED, MissionStatus.FAILED -> Danger
                MissionStatus.RUNNING -> Amber
                else -> TextDim
            }
            HiveCard(onClick = { onOpen(mission.id) }, accent = statusColor) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(mission.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Pill(mission.status.name.lowercase(), statusColor)
                }
                if (mission.brief.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(mission.brief, fontSize = 12.sp, color = TextDim)
                }
                Spacer(Modifier.height(6.dp))
                Text("created ${timeAgo(mission.createdAt)} ago", fontSize = 10.sp, color = TextDim)
            }
        }
    }
}
