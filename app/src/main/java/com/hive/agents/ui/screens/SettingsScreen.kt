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
import com.hive.agents.data.Models
import com.hive.agents.data.VaultKeys
import com.hive.agents.ui.BackRow
import com.hive.agents.ui.EmptyState
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.LabeledField
import com.hive.agents.ui.Pill
import com.hive.agents.ui.SectionHeader
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.TextDim

@Composable
fun SettingsScreen(
    state: HiveState,
    keys: VaultKeys,
    onSaveKeys: (VaultKeys) -> Unit,
    onSaveSettings: (com.hive.agents.data.Settings) -> Unit,
    onAutopilot: (Boolean) -> Unit,
    onForget: (String) -> Unit,
    onBack: () -> Unit
) {
    var nim by remember(keys) { mutableStateOf(keys.nimApiKey) }
    var mailKey by remember(keys) { mutableStateOf(keys.agentMailApiKey) }
    var mailInbox by remember(keys) { mutableStateOf(keys.agentMailInbox) }
    var stripeKey by remember(keys) { mutableStateOf(keys.stripeSecretKey) }
    var github by remember(keys) { mutableStateOf(keys.githubToken) }
    var business by remember(state.settings) { mutableStateOf(state.settings.businessName) }
    var ein by remember(state.settings) { mutableStateOf(state.settings.businessEin) }
    var goal by remember(state.settings) { mutableStateOf(state.settings.goal) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { BackRow("Home", onBack) }

        item {
            Text("Setup", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Keys are encrypted on this device in hive-secrets.json and survive app updates.",
                fontSize = 11.sp,
                color = TextDim
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(if (keys.hasNim) "model ok" else "model missing", if (keys.hasNim) Good else Amber)
                Pill(if (keys.hasMail) "email ok" else "email missing", if (keys.hasMail) Good else Amber)
                Pill(if (keys.hasStripe) "stripe ok" else "stripe missing", if (keys.hasStripe) Good else Amber)
            }
        }

        item {
            SectionHeader("KEYS", "never committed, never in the APK")
            LabeledField("NVIDIA API key", nim, secret = true) { nim = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("AgentMail API key", mailKey, secret = true) { mailKey = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Agent inbox address", mailInbox) { mailInbox = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Stripe secret key", stripeKey, secret = true) { stripeKey = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("GitHub token", github, secret = true) { github = it }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onSaveKeys(
                    keys.copy(
                        nimApiKey = nim.trim(),
                        agentMailApiKey = mailKey.trim(),
                        agentMailInbox = mailInbox.trim(),
                        stripeSecretKey = stripeKey.trim(),
                        githubToken = github.trim()
                    )
                )
            }) { Text("Save keys") }
        }

        item {
            SectionHeader("MODEL", "free NVIDIA tier")
            Models.all.forEach { model ->
                HiveCard(
                    accent = if (state.settings.model == model) Amber else null,
                    onClick = { onSaveSettings(state.settings.copy(model = model)) }
                ) {
                    Text(Models.label(model), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(model, fontSize = 10.sp, color = TextDim)
                }
            }
        }

        item {
            SectionHeader("BUSINESS", "every bot is told who it works for")
            LabeledField("Business name", business) { business = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("EIN", ein) { ein = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Standing goal", goal, singleLine = false) { goal = it }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onSaveSettings(
                    state.settings.copy(businessName = business, businessEin = ein, goal = goal)
                )
            }) { Text("Save business") }
        }

        item {
            SectionHeader("AUTOPILOT", "background shifts every 15 minutes while the phone has network")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    if (state.settings.autopilot) "Running shifts automatically" else "Off - only manual shifts",
                    fontSize = 12.sp,
                    color = TextDim
                )
                androidx.compose.material3.Switch(
                    checked = state.settings.autopilot,
                    onCheckedChange = { onAutopilot(it) }
                )
            }
        }

        item {
            SectionHeader("MEMORY", "${state.memory.size} entries")
            if (state.memory.isEmpty()) {
                EmptyState("The team has not saved anything yet.")
            }
        }

        items(state.memory.sortedByDescending { it.createdAt }.take(40)) { entry ->
            HiveCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.kind.name.lowercase(), fontSize = 10.sp, color = TextDim)
                    TextButton(onClick = { onForget(entry.id) }) { Text("Forget", fontSize = 10.sp) }
                }
                Text(entry.text, fontSize = 12.sp)
            }
        }
    }
}
