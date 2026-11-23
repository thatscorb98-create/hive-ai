package com.hive.agents.ui

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hive.agents.browser.AgentBrowserActivity
import com.hive.agents.data.Models
import com.hive.agents.ui.screens.BrowsersScreen
import com.hive.agents.ui.screens.ChatScreen
import com.hive.agents.ui.screens.HiveVizScreen
import com.hive.agents.ui.screens.HomeScreen
import com.hive.agents.ui.screens.InboxScreen
import com.hive.agents.ui.screens.MissionScreen
import com.hive.agents.ui.screens.MissionsScreen
import com.hive.agents.ui.screens.SalesScreen
import com.hive.agents.ui.screens.SettingsScreen
import com.hive.agents.ui.screens.StudioScreen
import com.hive.agents.ui.screens.TeamScreen
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Danger
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.Ink
import com.hive.agents.ui.theme.Panel
import com.hive.agents.ui.theme.TextDim

enum class HiveScreen {
    HOME, MISSIONS, MISSION, TEAM, CHAT, BUSINESS, STUDIO, INBOX, BROWSERS, VIZ, SETTINGS
}

@Composable
fun HiveApp(vm: HiveViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by vm.pendingApproval.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val keys by vm.keys.collectAsStateWithLifecycle()
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val pendingBrowser by vm.pendingBrowser.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var screen by remember { mutableStateOf(HiveScreen.HOME) }
    var openMission by remember { mutableStateOf<String?>(null) }

    // The agent asked for its browser: hand the real window to the human.
    // SINGLE_TOP + CLEAR_TOP reuses the window that is already open for this bot (and brings it
    // back to the front) instead of starting a second copy, so a repeated request cannot loop.
    LaunchedEffect(pendingBrowser) {
        val request = pendingBrowser ?: return@LaunchedEffect
        vm.clearBrowserRequest()
        val intent = Intent(context, AgentBrowserActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AgentBrowserActivity.EXTRA_AGENT_ID, request.agentId)
            putExtra(AgentBrowserActivity.EXTRA_AGENT_NAME, request.agentName)
            putExtra(AgentBrowserActivity.EXTRA_URL, request.url)
        }
        runCatching { context.startActivity(intent) }
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                NavigationBarItem(
                    selected = screen == HiveScreen.HOME ||
                        screen == HiveScreen.MISSIONS || screen == HiveScreen.MISSION,
                    onClick = { screen = HiveScreen.HOME },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Home", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = screen == HiveScreen.TEAM,
                    onClick = { screen = HiveScreen.TEAM },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("Agents", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = screen == HiveScreen.CHAT,
                    onClick = { screen = HiveScreen.CHAT },
                    icon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    label = { Text("Chat", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = screen == HiveScreen.BUSINESS,
                    onClick = { screen = HiveScreen.BUSINESS },
                    icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                    label = { Text("Business", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = screen == HiveScreen.SETTINGS,
                    onClick = { screen = HiveScreen.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Setup", fontSize = 10.sp) }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Ink)
        ) {
            Header(
                model = Models.label(state.settings.model),
                hasKey = keys.hasNim,
                onSetup = { screen = HiveScreen.SETTINGS }
            )

            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Amber)
            }

            if (notice.isNotBlank()) {
                Surface(color = Panel, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            notice,
                            fontSize = 12.sp,
                            color = if (notice.startsWith("https://")) Good else TextDim,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { vm.dismissNotice() }) { Text("Dismiss") }
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                when (screen) {
                    HiveScreen.HOME -> HomeScreen(
                        state = state,
                        autopilot = state.settings.autopilot,
                        busy = busy,
                        onCommand = { vm.command(it) },
                        onRunShift = { vm.runShift() },
                        onOpenViz = { screen = HiveScreen.VIZ },
                        onOpenStudio = { screen = HiveScreen.STUDIO },
                        onOpenBrowsers = { screen = HiveScreen.BROWSERS },
                        onOpenInbox = { screen = HiveScreen.INBOX },
                        onOpenMissions = { screen = HiveScreen.MISSIONS },
                        onOpenMission = { id -> openMission = id; screen = HiveScreen.MISSION }
                    )

                    HiveScreen.MISSIONS -> MissionsScreen(
                        state = state,
                        onCreate = { t, b -> vm.createMission(t, b) },
                        onOpen = { id -> openMission = id; screen = HiveScreen.MISSION },
                        onOpenViz = { screen = HiveScreen.VIZ },
                        onOpenStudio = { screen = HiveScreen.STUDIO },
                        onOpenInbox = { screen = HiveScreen.INBOX },
                        onOpenBrowsers = { screen = HiveScreen.BROWSERS }
                    )

                    HiveScreen.MISSION -> MissionScreen(
                        state = state,
                        missionId = openMission,
                        onBack = { screen = HiveScreen.HOME },
                        onStart = { vm.startMission(it) },
                        onDelete = { vm.deleteMission(it); screen = HiveScreen.HOME }
                    )

                    HiveScreen.TEAM -> TeamScreen(
                        state = state,
                        onHire = { r, n, c -> vm.hire(r, n, c) },
                        onDm = { from, to, text -> vm.directMessage(from, to, text) },
                        onOpenViz = { screen = HiveScreen.VIZ }
                    )

                    HiveScreen.CHAT -> ChatScreen(
                        state = state,
                        onSend = { text ->
                            val target = state.orchestrator?.id
                            if (target != null) vm.messageAgent(target, text)
                        }
                    )

                    HiveScreen.BUSINESS -> SalesScreen(
                        state = state,
                        onAddProspect = { c, e, v -> vm.addProspect(c, e, v) },
                        onMove = { id, stage -> vm.moveProspect(id, stage) },
                        onPay = { vm.createPaymentLink(it) },
                        onAddService = { n, p -> vm.addService(n, p) },
                        onAddProduct = { n, d, p -> vm.addProduct(n, d, p, com.hive.agents.data.DeliverableKind.OTHER) },
                        onCheckout = { vm.createCheckout(it) }
                    )

                    HiveScreen.STUDIO -> StudioScreen(
                        state = state,
                        onAddDeliverable = { t, b -> vm.addDeliverable(t, b) },
                        onSuggestion = { t, p -> vm.addSuggestion(t, p) },
                        onCopied = { vm.markSuggestionCopied(it) },
                        onBack = { screen = HiveScreen.HOME }
                    )

                    HiveScreen.INBOX -> InboxScreen(
                        state = state,
                        inbox = inbox,
                        onRefresh = { vm.refreshInbox(it) },
                        onBack = { screen = HiveScreen.HOME }
                    )

                    HiveScreen.BROWSERS -> BrowsersScreen(
                        state = state,
                        onOpen = { agentId -> vm.openBrowser(agentId) },
                        onBack = { screen = HiveScreen.HOME }
                    )

                    HiveScreen.VIZ -> HiveVizScreen(
                        state = state,
                        onBack = { screen = HiveScreen.HOME }
                    )

                    HiveScreen.SETTINGS -> SettingsScreen(
                        state = state,
                        keys = keys,
                        onSaveKeys = { vm.saveKeys(it) },
                        onSaveSettings = { vm.updateSettings(it) },
                        onAutopilot = { vm.setAutopilot(it) },
                        onForget = { vm.forgetMemory(it) },
                        onBack = { screen = HiveScreen.HOME }
                    )
                }
            }
        }
    }

    ApprovalDialog(pending) { vm.answerApproval(it) }
}

@Composable
private fun Header(model: String, hasKey: Boolean, onSetup: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(10.dp)
                .height(10.dp)
                .background(if (hasKey) Good else Danger)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("HIVE", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Amber)
            Text(model, fontSize = 10.sp, color = TextDim)
        }
        if (!hasKey) {
            TextButton(onClick = onSetup) { Text("Add key", fontSize = 12.sp) }
        }
    }
}

@Composable
fun BackRow(label: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        TextButton(onClick = onBack) { Text("< $label", fontSize = 12.sp, color = TextDim) }
    }
}
