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
import com.hive.agents.data.Prospect
import com.hive.agents.data.ProspectStage
import com.hive.agents.ui.EmptyState
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.LabeledField
import com.hive.agents.ui.Pill
import com.hive.agents.ui.SectionHeader
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.TextDim

@Composable
fun SalesScreen(
    state: HiveState,
    onAddProspect: (String, String, String) -> Unit,
    onMove: (String, ProspectStage) -> Unit,
    onPay: (String) -> Unit,
    onAddService: (String, String) -> Unit
) {
    var company by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }
    var servicePrice by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Sales", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Stripe links are created only when a buyer is ready to pay.",
                    fontSize = 11.sp,
                    color = TextDim
                )
            }
        }

        item { SectionHeader("PIPELINE", "${state.prospects.size} leads") }

        if (state.prospects.isEmpty()) {
            item { EmptyState("No leads yet. Add one, or let a bot qualify inbound mail.") }
        }

        items(state.prospects) { prospect ->
            HiveCard(accent = stageColor(prospect)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(prospect.company, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Pill(prospect.stage.name.lowercase(), stageColor(prospect))
                }
                if (prospect.email.isNotBlank()) {
                    Text(prospect.email, fontSize = 11.sp, color = TextDim)
                }
                Text(
                    if (prospect.valueCents > 0) "$${prospect.valueCents / 100.0}" else "no value set",
                    fontSize = 12.sp,
                    color = TextDim
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { onMove(prospect.id, nextStage(prospect.stage)) }) {
                        Text("Advance", fontSize = 11.sp)
                    }
                    TextButton(onClick = { onPay(prospect.id) }) {
                        Text("Create Stripe link", fontSize = 11.sp, color = Amber)
                    }
                }
            }
        }

        item {
            SectionHeader("NEW LEAD", "company and an optional deal value")
            LabeledField("Company", company) { company = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Email", email) { email = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Deal value (USD)", value) { value = it }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onAddProspect(company, email, value)
                company = ""; email = ""; value = ""
            }) { Text("Add lead") }
        }

        item {
            SectionHeader("SERVICES", "what the business sells")
            if (state.services.isEmpty()) {
                EmptyState("No services listed.")
            }
            state.services.forEach { service ->
                HiveCard {
                    Text(service.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("$${service.priceCents / 100.0}", fontSize = 11.sp, color = Good)
                }
            }
            Spacer(Modifier.height(6.dp))
            LabeledField("Service name", serviceName) { serviceName = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Price (USD)", servicePrice) { servicePrice = it }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onAddService(serviceName, servicePrice)
                serviceName = ""; servicePrice = ""
            }) { Text("Add service") }
        }
    }
}

private fun stageColor(prospect: Prospect) = when (prospect.stage) {
    ProspectStage.WON -> Good
    ProspectStage.LOST -> TextDim
    ProspectStage.NEGOTIATING -> Amber
    else -> Amber
}

private fun nextStage(stage: ProspectStage): ProspectStage = when (stage) {
    ProspectStage.NEW -> ProspectStage.QUALIFIED
    ProspectStage.QUALIFIED -> ProspectStage.PITCHED
    ProspectStage.PITCHED -> ProspectStage.NEGOTIATING
    ProspectStage.NEGOTIATING -> ProspectStage.WON
    ProspectStage.WON -> ProspectStage.WON
    ProspectStage.LOST -> ProspectStage.LOST
}
