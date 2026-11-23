package com.hive.agents.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.agent.ApprovalRequest
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Danger
import com.hive.agents.ui.theme.PanelHigh
import com.hive.agents.ui.theme.TextDim

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextDim)
        if (subtitle != null) {
            Text(subtitle, fontSize = 12.sp, color = TextDim)
        }
    }
}

@Composable
fun HiveCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PanelHigh)
    ) {
        if (accent != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(accent)
            )
        }
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun Pill(text: String, color: Color = Amber) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextDim, fontSize = 13.sp)
    }
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    secret: Boolean = false,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        visualTransformation = if (secret) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        }
    )
}

@Composable
fun RowButtons(vararg buttons: Pair<String, () -> Unit>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        buttons.forEach { (label, action) ->
            TextButton(onClick = action) { Text(label) }
        }
    }
}

@Composable
fun ApprovalDialog(request: ApprovalRequest?, onAnswer: (Boolean) -> Unit) {
    if (request == null) return
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(request.title, fontSize = 16.sp) },
        text = {
            Column {
                Text("${request.agentName} wants to run ${request.tool}.", fontSize = 12.sp, color = TextDim)
                Spacer(Modifier.height(8.dp))
                Text(request.detail, fontSize = 13.sp)
                if (request.irreversible) {
                    Spacer(Modifier.height(8.dp))
                    Text("This cannot be undone.", fontSize = 12.sp, color = Danger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(true) }) { Text("Allow once") }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false) }) { Text("Deny") }
        }
    )
}

fun timeAgo(ts: Long): String {
    val delta = (System.currentTimeMillis() - ts) / 1000
    return when {
        delta < 60 -> "now"
        delta < 3600 -> "${delta / 60}m"
        delta < 86_400 -> "${delta / 3600}h"
        else -> "${delta / 86_400}d"
    }
}
