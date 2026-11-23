package com.hive.agents.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.data.HiveState
import com.hive.agents.data.MessageKind
import com.hive.agents.data.Models
import com.hive.agents.data.ProspectStage
import com.hive.agents.ui.AgentAvatar
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.Pill
import com.hive.agents.ui.SectionHeader
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.PanelHigh
import com.hive.agents.ui.theme.Teal
import com.hive.agents.ui.theme.TextDim
import com.hive.agents.ui.theme.TextMain
import com.hive.agents.ui.timeAgo

/**
 * The dashboard. It answers three questions at a glance: is the company running, who is on the
 * crew, and what did they just do. Commands and conversation live in Chat; this screen stays an
 * overseer's board rather than a second control panel.
 */
@Composable
fun HomeScreen(
    state: HiveState,
    hasKey: Boolean,
    autopilot: Boolean,
    loopOn: Boolean,
    busy: Boolean,
    onRunShift: () -> Unit,
    onOpenViz: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenTeam: () -> Unit,
    onSetup: () -> Unit
) {
    val sales = state.salesMade
    val revenueCents = state.revenueCents
    val pipelineCents = state.prospects
        .filter { it.stage != ProspectStage.WON && it.stage != ProspectStage.LOST }
        .sumOf { it.valueCents }
    val activity = state.messages.sortedByDescending { it.createdAt }.take(6)
    val live = autopilot && hasKey

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("HIVE", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Amber)
                    Text(
                        "${state.agents.size} bots - ${Models.label(state.settings.model)}",
                        fontSize = 10.sp,
                        color = TextDim
                    )
                }
                Box(
                    Modifier
                        .width(8.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (live) Good else TextDim)
                )
                Spacer(Modifier.width(5.dp))
                Text(if (loopOn) "LIVE" else if (autopilot) "ON" else "OFF", fontSize = 9.sp, color = TextDim)
                TextButton(onClick = onOpenViz) { Text("3D", fontSize = 12.sp, color = Amber) }
            }
        }

        item {
            HiveCard(accent = Amber) {
                Text(
                    when {
                        loopOn -> "COMPANY LOOP RUNNING"
                        autopilot -> "AUTOPILOT READY"
                        else -> "AUTOPILOT PAUSED"
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (live) Good else TextDim
                )
                Text(
                    if (autopilot) "The company is running itself." else "Your company is ready.",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$sales", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Good)
                    Text(
                        "  sales made",
                        fontSize = 13.sp,
                        color = TextDim,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                }
                Text(
                    "$${"%.2f".format(revenueCents / 100.0)} collected - pipeline " +
                        "$${"%.2f".format(pipelineCents / 100.0)} across ${state.prospects.size} leads",
                    fontSize = 11.sp,
                    color = TextDim
                )
                if (!hasKey) {
                    Spacer(Modifier.height(6.dp))
                    Text("Add your NVIDIA key and Chief can start the company.", fontSize = 11.sp, color = Amber)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRunShift, enabled = hasKey && !busy) {
                        Text(if (busy) "Working..." else "Run a shift")
                    }
                    OutlinedButton(onClick = onOpenViz) { Text("3D station") }
                }
                if (!hasKey) {
                    TextButton(onClick = onSetup) { Text("Add NVIDIA key", fontSize = 12.sp) }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricTile("Sales", "$sales", Good, Modifier.weight(1f))
                MetricTile("Bots", "${state.agents.size}", Teal, Modifier.weight(1f))
                MetricTile("Products", "${state.products.size}", Amber, Modifier.weight(1f))
            }
        }

        item {
            SectionHeader("CREW", "${state.agents.size} bots - tap to manage")
        }

        item {
            if (state.agents.isEmpty()) {
                HiveCard {
                    Text("No crew yet. Chief will hire the first team after a shift.", fontSize = 12.sp, color = TextDim)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.agents.forEach { agent ->
                        Column(
                            Modifier
                                .width(62.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenTeam)
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AgentAvatar(agent, size = 38.dp, selected = agent.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                agent.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (agent.primary) "lead" else agent.role,
                                fontSize = 8.sp,
                                color = TextDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("RECENT WORK", "${state.deliverables.size} deliverables - samples are customer-ready")
        }

        if (state.deliverables.isEmpty()) {
            item {
                HiveCard {
                    Text("Nothing produced yet. The crew will make samples as it learns what sells.", fontSize = 11.sp, color = TextDim)
                }
            }
        }

        items(state.deliverables.sortedByDescending { it.createdAt }.take(3)) { deliverable ->
            HiveCard(accent = if (deliverable.isSample) Amber else Teal) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(deliverable.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Pill(if (deliverable.isSample) "sample" else deliverable.kind.name.lowercase(), if (deliverable.isSample) Amber else Teal)
                }
                Text(deliverable.body.replace("\n", " ").take(160), fontSize = 10.sp, color = TextDim, maxLines = 2)
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDim, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenChat) { Text("Open chat", fontSize = 11.sp) }
            }
        }

        if (activity.isEmpty()) {
            item {
                HiveCard {
                    Text("The team is starting up. Open Chat and say hello to Chief.", fontSize = 12.sp, color = TextDim)
                }
            }
        }

        items(activity) { message ->
            val name = if (message.fromUser) "You" else state.agent(message.agentId)?.name ?: "System"
            val to = message.toAgentId.takeIf { it.isNotBlank() }?.let { state.agent(it)?.name }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenChat)
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.Top
            ) {
                val agent = state.agent(message.agentId)
                if (agent != null && !message.fromUser) {
                    AgentAvatar(agent, size = 26.dp, showOnline = false)
                } else {
                    Box(
                        Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(PanelHigh),
                        contentAlignment = Alignment.Center
                    ) { Text("Y", fontSize = 10.sp, color = Teal) }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (to != null) "$name -> $to" else name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (message.kind == MessageKind.HANDOFF) Good else TextDim
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(timeAgo(message.createdAt), fontSize = 9.sp, color = TextDim)
                    }
                    Text(
                        message.body.replace("\n", " ").take(120),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(if (state.agents.size > 1) "crew online" else "hiring", if (state.agents.size > 1) Good else Amber)
                Pill("${state.deliverables.size} deliverables", Teal)
            }
            Spacer(Modifier.height(22.dp))
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
