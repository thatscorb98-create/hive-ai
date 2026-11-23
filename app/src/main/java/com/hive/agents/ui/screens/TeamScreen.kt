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
import androidx.compose.material3.AlertDialog
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
import com.hive.agents.data.Agent
import com.hive.agents.data.HiveState
import com.hive.agents.ui.EmptyState
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.LabeledField
import com.hive.agents.ui.Pill
import com.hive.agents.ui.SectionHeader
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Teal
import com.hive.agents.ui.theme.TextDim

@Composable
fun TeamScreen(
    state: HiveState,
    onHire: (String, String, String) -> Unit,
    onDm: (String, String, String) -> Unit,
    onOpenViz: () -> Unit
) {
    var role by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var charter by remember { mutableStateOf("") }

    var dmFrom by remember { mutableStateOf<Agent?>(null) }
    var dmTo by remember { mutableStateOf<Agent?>(null) }
    var dmText by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("The team", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onOpenViz) { Text("3D view", fontSize = 12.sp) }
            }
        }

        if (state.agents.isEmpty()) {
            item { EmptyState("No bots yet.") }
        }

        items(state.agents) { agent ->
            HiveCard(accent = if (agent.primary) Amber else Teal) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(agent.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(agent.role, fontSize = 11.sp, color = TextDim)
                    }
                    if (agent.primary) Pill("primary", Amber)
                }
                if (agent.charter.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(agent.charter, fontSize = 12.sp, color = TextDim)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { dmFrom = agent; dmTo = state.agents.firstOrNull { it.id != agent.id } }) {
                        Text("Message another bot", fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            SectionHeader("HIRE A SPECIALIST", "The orchestrator can do this itself, or you can name the role.")
            LabeledField("Role", role) { role = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Bot name", name) { name = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Charter", charter) { charter = it }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onHire(role, name, charter)
                role = ""; name = ""; charter = ""
            }) { Text("Hire") }
        }
    }

    if (dmFrom != null) {
        AlertDialog(
            onDismissRequest = { dmFrom = null },
            title = { Text("Message as ${dmFrom?.name}", fontSize = 15.sp) },
            text = {
                Column {
                    Text("To:", fontSize = 11.sp, color = TextDim)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.agents.filter { it.id != dmFrom?.id }.take(4).forEach { candidate ->
                            TextButton(onClick = { dmTo = candidate }) {
                                Text(
                                    candidate.name,
                                    fontSize = 11.sp,
                                    color = if (dmTo?.id == candidate.id) Amber else TextDim
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LabeledField("What to say", dmText, singleLine = false) { dmText = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val from = dmFrom
                    val to = dmTo
                    if (from != null && to != null) {
                        onDm(from.id, to.id, dmText)
                        dmText = ""
                        dmFrom = null
                    }
                }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { dmFrom = null }) { Text("Cancel") } }
        )
    }
}
