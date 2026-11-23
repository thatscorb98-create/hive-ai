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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.data.HiveState
import com.hive.agents.ui.BackRow
import com.hive.agents.ui.EmptyState
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.LabeledField
import com.hive.agents.ui.SectionHeader
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.Teal
import com.hive.agents.ui.theme.TextDim
import com.hive.agents.ui.timeAgo

/** Finished work plus the suggestion box that writes prompts for a coding agent. */
@Composable
fun StudioScreen(
    state: HiveState,
    onAddDeliverable: (String, String) -> Unit,
    onSuggestion: (String, String) -> Unit,
    onCopied: (String) -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var idea by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { BackRow("Missions", onBack) }

        item {
            Text("Studio", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Websites, graphics, copy and digital goods the team produced.", fontSize = 11.sp, color = TextDim)
        }

        item { SectionHeader("DELIVERABLES", "${state.deliverables.size} saved") }

        if (state.deliverables.isEmpty()) {
            item { EmptyState("No deliverables yet.") }
        }

        items(state.deliverables.sortedByDescending { it.createdAt }) { item ->
            HiveCard(accent = Good) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(item.kind.name.lowercase(), fontSize = 10.sp, color = TextDim)
                }
                Text("by ${state.agent(item.agentId)?.name ?: "human"} - ${timeAgo(item.createdAt)}", fontSize = 10.sp, color = TextDim)
                if (item.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(item.body.take(600), fontSize = 12.sp)
                }
            }
        }

        item {
            SectionHeader("ADD DELIVERABLE", "paste finished work")
            LabeledField("Title", title) { title = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Body", body, singleLine = false) { body = it }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onAddDeliverable(title, body)
                title = ""; body = ""
            }) { Text("Save") }
        }

        item {
            SectionHeader("SUGGESTION BOX", "a prompt you can paste into any coding agent")
            LabeledField("What should improve?", idea, singleLine = false) { idea = it }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onSuggestion(idea.take(60), idea)
                idea = ""
            }) { Text("Add suggestion") }
        }

        if (state.suggestions.isEmpty()) {
            item { EmptyState("Nothing suggested yet.") }
        }

        items(state.suggestions.sortedByDescending { it.createdAt }) { suggestion ->
            HiveCard(accent = Teal) {
                Text(suggestion.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(suggestion.asPrompt(), fontSize = 11.sp, color = TextDim)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(suggestion.asPrompt()))
                        onCopied(suggestion.id)
                    }) { Text("Copy prompt", fontSize = 11.sp, color = Amber) }
                    if (suggestion.copied) {
                        Text("copied", fontSize = 10.sp, color = Good)
                    }
                }
            }
        }
    }
}
