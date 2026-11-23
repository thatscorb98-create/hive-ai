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
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.Teal
import com.hive.agents.ui.theme.TextDim

/**
 * Every bot's browser in one place. Open any bot's window to watch it work, log in yourself when
 * a site asks, then hand the same window back. One window is live at a time: opening another bot
 * brings that bot's window forward, and the bot keeps the page it was on.
 */
@Composable
fun BrowsersScreen(
    state: HiveState,
    currentAgent: String?,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    showBack: Boolean = true
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (showBack) item { BackRow("Home", onBack) }

        item {
            Text("Browsers", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "One window per bot. Open a bot's browser to watch it, sign in yourself, or take over.",
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
            item { EmptyState("No bots yet. Run a shift and your lead bot will start hiring.") }
        }

        items(state.agents) { agent ->
            val live = currentAgent == agent.id
            HiveCard(accent = if (live) Good else Teal) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(agent.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${agent.role} - profile ${agent.profileId.take(8)}", fontSize = 10.sp, color = TextDim)
                    }
                    Pill(if (live) "open now" else "ready", if (live) Good else Teal)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = { onOpen(agent.id) }) {
                        Text(if (live) "Bring to front" else "Open browser", fontSize = 12.sp)
                    }
                }
                if (live) {
                    Spacer(Modifier.height(4.dp))
                    Text("This window is attached to ${agent.name}. Its browser tools work right now.", fontSize = 10.sp, color = TextDim)
                }
            }
        }
    }
}
