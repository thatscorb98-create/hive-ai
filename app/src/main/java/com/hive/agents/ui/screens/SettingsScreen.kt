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
import androidx.compose.runtime.collectAsState
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
    onForget: (String) -> Unit
) {
    var nim by remember(keys) { mutableStateOf(keys.nimApiKey) }
    var mailKey by remember(keys) { mutableStateOf(keys.agentMailApiKey) }
    var mailInbox by remember(keys) { mutableStateOf(keys.agentMailInbox) }
    var stripeKey by remember(keys) { mutableStateOf(keys.stripeSecretKey) }
    var github by remember(keys) { mutableStateOf(keys.githubToken) }
    var storageUrl by remember(keys) { mutableStateOf(keys.storageUrl) }
    var storageKey by remember(keys) { mutableStateOf(keys.storageKey) }
    var googleKey by remember(keys) { mutableStateOf(keys.googleSearchKey) }
    var googleCx by remember(keys) { mutableStateOf(keys.googleSearchCx) }
    var business by remember(state.settings) { mutableStateOf(state.settings.businessName) }
    var ein by remember(state.settings) { mutableStateOf(state.settings.businessEin) }
    var goal by remember(state.settings) { mutableStateOf(state.settings.goal) }
    var blocklist by remember(state.settings) { mutableStateOf(state.settings.emailBlocklist) }
    var syncUrl by remember(state.settings) { mutableStateOf(state.settings.syncUrl) }
    var syncKey by remember(state.settings) { mutableStateOf(state.settings.syncKey) }
    val liveSyncStatus by com.hive.agents.net.LiveSync.status.collectAsState()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(10.dp))
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Keys are encrypted on this device in hive-secrets.json, with a backup copy, and survive an in-place app update.",
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
            Spacer(Modifier.height(6.dp))
            LabeledField("Storage URL", storageUrl) { storageUrl = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Storage key", storageKey, secret = true) { storageKey = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Google API key (optional)", googleKey, secret = true) { googleKey = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Google search engine id, cx (optional)", googleCx) { googleCx = it }
            Spacer(Modifier.height(4.dp))
            Text(
                "Add both Google fields to research with real Google results. Without them the team " +
                    "still searches with DuckDuckGo and Bing.",
                fontSize = 10.sp,
                color = TextDim
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onSaveKeys(
                    keys.copy(
                        nimApiKey = nim.trim(),
                        agentMailApiKey = mailKey.trim(),
                        agentMailInbox = mailInbox.trim(),
                        stripeSecretKey = stripeKey.trim(),
                        githubToken = github.trim(),
                        storageUrl = storageUrl.trim(),
                        storageKey = storageKey.trim(),
                        googleSearchKey = googleKey.trim(),
                        googleSearchCx = googleCx.trim()
                    )
                )
            }) { Text("Save keys") }
            Spacer(Modifier.height(4.dp))
            Text(
                "Uninstalling the app, or installing a build signed with a different key, clears this folder. " +
                    "Keep a copy of the keys somewhere safe.",
                fontSize = 10.sp,
                color = TextDim
            )
        }

        item {
            SectionHeader("LIVE SYNC", "optional - pushes a snapshot to your dashboard")
            LabeledField("Sync URL", syncUrl) { syncUrl = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Sync key", syncKey, secret = true) { syncKey = it }
            Spacer(Modifier.height(4.dp))
            Text(
                "Paste the URL and key from the dashboard's Settings -> Live sync card. Every change " +
                    "is pushed about two seconds after it settles, and the dashboard refreshes every " +
                    "four seconds. The key stays on this device; it is removed from the uploaded snapshot.",
                fontSize = 10.sp,
                color = TextDim
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onSaveSettings(
                    state.settings.copy(syncUrl = syncUrl.trim(), syncKey = syncKey.trim())
                )
            }) { Text("Save live sync") }
            if (syncKey.isNotBlank() && syncKey.trim().length < 16) {
                Spacer(Modifier.height(4.dp))
                Text("The key must be at least 16 characters.", fontSize = 10.sp, color = Amber)
            }
            if (liveSyncStatus.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(liveSyncStatus, fontSize = 10.sp, color = TextDim)
            }
        }

        item {
            SectionHeader("MODEL", "free NVIDIA tier - Hive checks the live catalog")
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
            SectionHeader("BUSINESS", "every bot is told who it works for; this profile is saved on this device")
            LabeledField("Business name", business) { business = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("EIN", ein) { ein = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Standing goal", goal, singleLine = false) { goal = it }
            Spacer(Modifier.height(6.dp))
            LabeledField(
                "Do not email (comma separated)",
                blocklist,
                singleLine = false
            ) { blocklist = it }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onSaveSettings(
                    state.settings.copy(
                        businessName = business,
                        businessEin = ein,
                        goal = goal,
                        emailBlocklist = blocklist
                    )
                )
            }) { Text("Save business") }
        }

        item {
            SectionHeader("AUTOPILOT", "the team loops through the pipeline while the app is open, then every 15 minutes in the background")
            ToggleRow(
                label = if (state.settings.autopilot) "Running shifts automatically" else "Off - only manual shifts",
                checked = state.settings.autopilot,
                onChange = onAutopilot
            )
            ToggleRow(
                label = "Use a different model per role (lead and product strong, research and sales fast, proofreader cheap)",
                checked = state.settings.routeModels,
                onChange = { onSaveSettings(state.settings.copy(routeModels = it)) }
            )
            ToggleRow(
                label = "Proofread every email and product description before it goes out",
                checked = state.settings.proofreadOutbound,
                onChange = { onSaveSettings(state.settings.copy(proofreadOutbound = it)) }
            )
            ToggleRow(
                label = "Ask me before a bot sends email",
                checked = state.settings.requireApprovalForEmail,
                onChange = { onSaveSettings(state.settings.copy(requireApprovalForEmail = it)) }
            )
            Text(
                "Payment links always need your approval. Every email and product description is proofread before it goes out.",
                fontSize = 10.sp,
                color = TextDim
            )
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

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = TextDim, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}
