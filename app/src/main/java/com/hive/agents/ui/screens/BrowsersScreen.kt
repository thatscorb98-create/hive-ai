package com.hive.agents.ui.screens

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.data.HiveState
import com.hive.agents.ui.BackRow
import com.hive.agents.ui.EmptyState
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.Pill
import com.hive.agents.ui.theme.Teal
import com.hive.agents.ui.theme.TextDim

/**
 * Each bot has its own browser window with its own address bar and history. Open a bot's
 * browser, sign in to whatever it needs, then let the bot drive that same window with its
 * browser tools (open, read, click, type).
 */
@Composable
fun BrowsersScreen(state: HiveState, onOpen: (String) -> Unit, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { BackRow("Home", onBack) }

        item {
            Text("Browsers", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "One window per bot. Log in yourself when a site asks, and the bot keeps that session.",
                fontSize = 11.sp,
                color = TextDim
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Android shares one cookie jar across WebViews, so treat a login as available to the team. " +
                    "Do not sign a bot into anything you would not trust the others with.",
                fontSize = 10.sp,
                color = TextDim
            )
            Spacer(Modifier.height(8.dp))
        }

        if (state.agents.isEmpty()) {
            item { EmptyState("No bots yet.") }
        }

        items(state.agents) { agent ->
            HiveCard(accent = Teal) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(agent.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${agent.role} - profile ${agent.profileId.take(8)}", fontSize = 10.sp, color = TextDim)
                    }
                    Pill(if (agent.active) "ready" else "idle", Teal)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = { onOpen(agent.id) }) { Text("Open browser", fontSize = 12.sp) }
                }
            }
        }
    }
}
