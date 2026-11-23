package com.hive.agents.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
 * Browser profiles. Each bot gets its own profile id so sessions do not bleed between roles.
 * The in-app browser host that drives these lands with checklist item 3.x; until then this
 * screen shows the profile and opens a URL in the device browser.
 */
@Composable
fun BrowsersScreen(state: HiveState, onBack: () -> Unit) {
    val context = LocalContext.current

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { BackRow("Missions", onBack) }

        item {
            Text("Browsers", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "One profile per bot, so cookies and logins stay with the role.",
                fontSize = 11.sp,
                color = TextDim
            )
            Spacer(Modifier.height(8.dp))
        }

        if (state.agents.isEmpty()) {
            item { EmptyState("No bots, no browsers.") }
        }

        items(state.agents) { agent ->
            HiveCard(accent = Teal) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(agent.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("profile ${agent.profileId.take(8)}", fontSize = 10.sp, color = TextDim)
                    }
                    Pill(if (agent.active) "ready" else "idle", Teal)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://html.duckduckgo.com/html/?q=${agent.role}")
                    )
                    runCatching { context.startActivity(intent) }
                }) { Text("Open a page", fontSize = 11.sp) }
            }
        }
    }
}
