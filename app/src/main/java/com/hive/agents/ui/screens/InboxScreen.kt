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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.data.HiveState
import com.hive.agents.data.InboxMail
import com.hive.agents.ui.BackRow
import com.hive.agents.ui.EmptyState
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.SectionHeader
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Teal
import com.hive.agents.ui.theme.TextDim
import com.hive.agents.ui.timeAgo

/** Per-agent mailboxes. Inbox comes from the network, sent mail is local history. */
@Composable
fun InboxScreen(
    state: HiveState,
    inbox: List<InboxMail>,
    onRefresh: (String) -> Unit,
    onBack: () -> Unit,
    showBack: Boolean = true
) {
    var selected by remember { mutableStateOf(state.agents.firstOrNull()?.id.orEmpty()) }
    var tab by remember { mutableStateOf("Inbox") }
    val agent = state.agent(selected)

    LaunchedEffect(state.agents) {
        if (state.agent(selected) == null) selected = state.agents.firstOrNull()?.id.orEmpty()
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (showBack) item { BackRow("Home", onBack) }

        item {
            Text("Email", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Every bot has its own address. Nothing is sent without approval.",
                fontSize = 11.sp,
                color = TextDim
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.agents, key = { it.id }) { candidate ->
                    TextButton(onClick = { selected = candidate.id }) {
                        Text(
                            candidate.name,
                            fontSize = 11.sp,
                            color = if (candidate.id == selected) Amber else TextDim
                        )
                    }
                }
            }
            Text(agent?.email?.ifBlank { "no address yet" } ?: "no agent", fontSize = 11.sp, color = Teal)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { tab = "Inbox" }) {
                    Text("Inbox", fontSize = 12.sp, color = if (tab == "Inbox") Amber else TextDim)
                }
                TextButton(onClick = { tab = "Sent" }) {
                    Text("Sent", fontSize = 12.sp, color = if (tab == "Sent") Amber else TextDim)
                }
                TextButton(onClick = { if (selected.isNotBlank()) onRefresh(selected) }) {
                    Text("Refresh", fontSize = 12.sp)
                }
            }
        }

        if (tab == "Inbox") {
            item { SectionHeader("INBOX", "${inbox.size} fetched") }
            if (inbox.isEmpty()) {
                item { EmptyState("No mail fetched. Add the AgentMail key in Setup, then refresh.") }
            }
            items(inbox) { mail ->
                HiveCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(mail.subject, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(timeAgo(mail.receivedAt), fontSize = 10.sp, color = TextDim)
                    }
                    Text(mail.from, fontSize = 11.sp, color = TextDim)
                    if (mail.preview.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(mail.preview, fontSize = 12.sp)
                    }
                }
            }
        } else {
            val sent = state.sentMail.filter { it.agentId == selected || agent?.email == it.from }
            item { SectionHeader("SENT", "${sent.size} messages") }
            if (sent.isEmpty()) {
                item { EmptyState("Nothing sent from this address yet.") }
            }
            items(sent) { mail ->
                HiveCard(accent = Teal) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(mail.subject, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(timeAgo(mail.sentAt), fontSize = 10.sp, color = TextDim)
                    }
                    Text("to ${mail.to}", fontSize = 11.sp, color = TextDim)
                    Spacer(Modifier.height(4.dp))
                    Text(mail.body.take(240), fontSize = 12.sp)
                }
            }
        }
    }
}
