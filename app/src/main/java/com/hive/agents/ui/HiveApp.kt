package com.hive.agents.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hive.agents.data.Models
import com.hive.agents.ui.screens.BrowsersScreen
import com.hive.agents.ui.screens.ChatScreen
import com.hive.agents.ui.screens.HiveVizScreen
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

enum class HiveScreen { MISSIONS, MISSION, TEAM, CHAT, INBOX, SALES, STUDIO, BROWSERS, VIZ, SETTINGS }

@Composable
fun HiveApp(vm: HiveViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by vm.pendingApproval.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val keys by vm.keys.collectAsStateWithLifecycle()
    val inbox by vm.inbox.collectAsStateWithLifecycle()

    var screen by remember { mutableStateOf(HiveScreen.MISSIONS) }
    var openMission by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                NavigationBarItem(
                    selected = screen == HiveScreen.MISSIONS || screen == HiveScreen.MISSION,
                    onClick = { screen = HiveScreen.MISSIONS },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Missions", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = screen == HiveScreen.TEAM,
                    onClick = { screen = HiveScreen.TEAM },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("Team", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = screen == HiveScreen.CHAT,
                    onClick = { screen = HiveScreen.CHAT },
                    icon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    label = { Text("Chat", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = screen == HiveScreen.SALES,
                    onClick = { screen = HiveScreen.SALES },
                    icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                    label = { Text("Sales", fontSize = 10.sp) }
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
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                        onBack = { screen = HiveScreen.MISSIONS },
                        onStart = { vm.startMission(it) },
                        onDelete = { vm.deleteMission(it); screen = HiveScreen.MISSIONS }
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

                    HiveScreen.INBOX -> InboxScreen(
                        state = state,
                        inbox = inbox,
                        onRefresh = { vm.refreshInbox(it) },
                        onBack = { screen = HiveScreen.MISSIONS }
                    )

                    HiveScreen.SALES -> SalesScreen(
                        state = state,
                        onAddProspect = { c, e, v -> vm.addProspect(c, e, v) },
                        onMove = { id, stage -> vm.moveProspect(id, stage) },
                        onPay = { vm.createPaymentLink(it) },
                        onAddService = { n, p -> vm.addService(n, p) }
                    )

                    HiveScreen.STUDIO -> StudioScreen(
                        state = state,
                        onAddDeliverable = { t, b -> vm.addDeliverable(t, b) },
                        onSuggestion = { t, p -> vm.addSuggestion(t, p) },
                        onCopied = { vm.markSuggestionCopied(it) },
                        onBack = { screen = HiveScreen.MISSIONS }
                    )

                    HiveScreen.BROWSERS -> BrowsersScreen(
                        state = state,
                        onBack = { screen = HiveScreen.MISSIONS }
                    )

                    HiveScreen.VIZ -> HiveVizScreen(
                        state = state,
                        onBack = { screen = HiveScreen.TEAM }
                    )

                    HiveScreen.SETTINGS -> SettingsScreen(
                        state = state,
                        keys = keys,
                        onSaveKeys = { vm.saveKeys(it) },
                        onSaveSettings = { vm.updateSettings(it) },
                        onForget = { vm.forgetMemory(it) },
                        onBack = { screen = HiveScreen.MISSIONS }
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
