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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import com.hive.agents.data.HiveState
import com.hive.agents.data.MessageKind
import com.hive.agents.ui.EmptyState
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.LabeledField
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.TextDim
import com.hive.agents.ui.timeAgo

@Composable
fun ChatScreen(state: HiveState, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messages = state.messages.sortedBy { it.createdAt }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("Team chat", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Every bot posts here. Direct messages between bots show as arrows.",
                        fontSize = 11.sp,
                        color = TextDim
                    )
                }
            }
            if (messages.isEmpty()) {
                item { EmptyState("Nothing said yet. Ask the team anything.") }
            }
            items(messages) { message ->
                val speaker = if (message.fromUser) "You" else state.agent(message.agentId)?.name ?: "System"
                HiveCard(accent = if (message.fromUser) Amber else null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (message.kind == MessageKind.DM) {
                                "$speaker -> ${state.agent(message.toAgentId)?.name ?: "?"}"
                            } else {
                                speaker
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDim
                        )
                        Text(timeAgo(message.createdAt), fontSize = 10.sp, color = TextDim)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(message.body, fontSize = 13.sp)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabeledField("Message the team", text, singleLine = false, modifier = Modifier.weight(1f)) { text = it }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = {
                if (text.isNotBlank()) {
                    onSend(text)
                    text = ""
                }
            }) { Text("Send") }
        }
    }
}
