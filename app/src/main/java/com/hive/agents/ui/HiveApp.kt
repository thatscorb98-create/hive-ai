package com.hive.agents.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hive.agents.browser.AgentBrowserActivity
import com.hive.agents.browser.BrowserHub
import com.hive.agents.ui.screens.BrowsersScreen
import com.hive.agents.ui.screens.ChatScreen
import com.hive.agents.ui.screens.HiveVizScreen
import com.hive.agents.ui.screens.HomeScreen
import com.hive.agents.ui.screens.InboxScreen
import com.hive.agents.ui.screens.SettingsScreen
import com.hive.agents.ui.screens.TeamScreen
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Danger
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.Ink
import com.hive.agents.ui.theme.Panel
import com.hive.agents.ui.theme.TextDim

enum class HiveScreen {
    HOME, TEAM, CHAT, INBOX, BROWSERS, VIZ, SETTINGS
}

@Composable
fun HiveApp(vm: HiveViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by vm.pendingApproval.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val loopOn by vm.loopOn.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val keys by vm.keys.collectAsStateWithLifecycle()
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val pendingBrowser by vm.pendingBrowser.collectAsStateWithLifecycle()
    val currentBrowser by BrowserHub.currentAgent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var screen by remember { mutableStateOf(HiveScreen.HOME) }

    // The company starts working when the app opens, without waiting for a button. LaunchedEffect
    // covers the first composition, when the activity may already be RESUMED and the observer
    // below would never receive its initial ON_RESUME.
    LaunchedEffect(Unit) { vm.onAppForeground() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.onAppForeground()
                Lifecycle.Event.ON_STOP -> vm.flushState()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The agent asked for its browser: hand the real window to the human.
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
                        screen == HiveScreen.VIZ || screen == HiveScreen.TEAM,
                    onClick = { screen = HiveScreen.HOME },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Home", fontSize = 9.sp) }
                )
                NavigationBarItem(
                    selected = screen == HiveScreen.CHAT,
                    onClick = { screen = HiveScreen.CHAT },
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    label = { Text("Chat", fontSize = 9.sp) }
                )
                NavigationBarItem(
                    selected = screen == HiveScreen.BROWSERS,
                    onClick = { screen = HiveScreen.BROWSERS },
                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("Browsers", fontSize = 9.sp) }
                )
                NavigationBarItem(
                    selected = screen == HiveScreen.INBOX,
                    onClick = { screen = HiveScreen.INBOX },
                    icon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    label = { Text("Email", fontSize = 9.sp) }
                )
                NavigationBarItem(
                    selected = screen == HiveScreen.SETTINGS,
                    onClick = { screen = HiveScreen.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings", fontSize = 9.sp) }
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
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Amber)
            }

            if (notice.isNotBlank()) {
                val bad = notice.contains("fail", true) ||
                    notice.contains("error", true) ||
                    notice.contains("missing", true)
                Surface(color = Panel, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            notice,
                            fontSize = 11.sp,
                            color = if (bad) Danger else Good,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { vm.dismissNotice() }) { Text("Dismiss", fontSize = 10.sp) }
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                when (screen) {
                    HiveScreen.HOME -> HomeScreen(
                        state = state,
                        hasKey = keys.hasNim,
                        autopilot = state.settings.autopilot,
                        loopOn = loopOn,
                        busy = busy,
                        onRunShift = { vm.runShift() },
                        onOpenViz = { screen = HiveScreen.VIZ },
                        onOpenChat = { screen = HiveScreen.CHAT },
                        onOpenTeam = { screen = HiveScreen.TEAM },
                        onSetup = { screen = HiveScreen.SETTINGS }
                    )

                    HiveScreen.TEAM -> TeamScreen(
                        state = state,
                        onHire = { r, n, c -> vm.hire(r, n, c) },
                        onDelete = { vm.deleteAgent(it) },
                        onDm = { from, to, text -> vm.directMessage(from, to, text) },
                        onOpenViz = { screen = HiveScreen.VIZ }
                    )

                    HiveScreen.CHAT -> ChatScreen(
                        state = state,
                        busy = busy,
                        onSend = { agentId, text -> vm.messageAgent(agentId, text) },
                        onOpenViz = { screen = HiveScreen.VIZ },
                        onOpenTeam = { screen = HiveScreen.TEAM }
                    )

                    HiveScreen.INBOX -> InboxScreen(
                        state = state,
                        inbox = inbox,
                        onRefresh = { vm.refreshInbox(it) },
                        onBack = { screen = HiveScreen.HOME },
                        showBack = false
                    )

                    HiveScreen.BROWSERS -> BrowsersScreen(
                        state = state,
                        currentAgent = currentBrowser,
                        onOpen = { agentId -> vm.openBrowser(agentId) },
                        onBack = { screen = HiveScreen.HOME },
                        showBack = false
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
                        onForget = { vm.forgetMemory(it) }
                    )
                }
            }
        }
    }

    ApprovalDialog(pending) { vm.answerApproval(it) }
}

@Composable
fun BackRow(label: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text("< $label", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(4.dp))
    }
}
