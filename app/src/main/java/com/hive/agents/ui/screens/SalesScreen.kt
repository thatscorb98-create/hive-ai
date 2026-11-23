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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.data.HiveState
import com.hive.agents.data.Prospect
import com.hive.agents.data.ProspectStage
import com.hive.agents.ui.EmptyState
import com.hive.agents.ui.HiveCard
import com.hive.agents.ui.Pill
import com.hive.agents.ui.SectionHeader
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.Teal
import com.hive.agents.ui.theme.TextDim

/**
 * What the company is selling and who is buying. Deliberately read-only: the bots add products
 * and leads, and the human only nudges a lead forward or creates a payment link at the moment a
 * buyer is ready.
 */
@Composable
fun SalesScreen(
    state: HiveState,
    onMove: (String, ProspectStage) -> Unit,
    onPay: (String) -> Unit
) {
    val sales = state.salesMade
    val collected = state.revenueCents
    val pipelineCents = state.prospects
        .filter { it.stage != ProspectStage.WON && it.stage != ProspectStage.LOST }
        .sumOf { it.valueCents }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Business", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${state.settings.businessName} - progress is measured by sales made.",
                    fontSize = 11.sp,
                    color = TextDim
                )
            }
        }

        item {
            HiveCard(accent = Good) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text("$sales", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Good)
                        Text("sales made", fontSize = 11.sp, color = TextDim)
                    }
                    Column {
                        Text(
                            "$${"%.2f".format(collected / 100.0)}",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Amber
                        )
                        Text("collected", fontSize = 11.sp, color = TextDim)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Pipeline $${"%.2f".format(pipelineCents / 100.0)} across ${state.prospects.size} leads",
                    fontSize = 11.sp,
                    color = TextDim
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tell your lead bot what to add in Chat. The team builds products and lead lists; " +
                        "a Stripe link is only created when a buyer is ready to pay.",
                    fontSize = 10.sp,
                    color = TextDim
                )
            }
        }

        item { SectionHeader("DIGITAL PRODUCTS", "what the bots sell") }

        if (state.products.isEmpty()) {
            item { EmptyState("No products yet. Ask a bot to create one.") }
        }

        items(state.products) { product ->
            HiveCard(accent = Good) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(product.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Pill(product.status, if (product.status == "on sale") Good else Amber)
                }
                if (product.description.isNotBlank()) {
                    Text(product.description, fontSize = 11.sp, color = TextDim)
                }
                Text("$${"%.2f".format(product.priceCents / 100.0)}", fontSize = 13.sp, color = Good)
                if (product.checkoutUrl.isNotBlank()) {
                    Text(product.checkoutUrl, fontSize = 10.sp, color = TextDim)
                }
            }
        }

        item { SectionHeader("PIPELINE", "${state.prospects.size} leads") }

        if (state.prospects.isEmpty()) {
            item { EmptyState("No leads yet. Your sales bot builds the list from real businesses.") }
        }

        items(state.prospects) { prospect ->
            HiveCard(accent = stageColor(prospect)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(prospect.company, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (prospect.paid) Pill("paid", Good)
                        if (prospect.delivered) Pill("delivered", Teal)
                        Pill(prospect.stage.name.lowercase(), stageColor(prospect))
                    }
                }
                if (prospect.contactName.isNotBlank() || prospect.email.isNotBlank()) {
                    Text(
                        listOf(prospect.contactName, prospect.email).filter { it.isNotBlank() }.joinToString(" - "),
                        fontSize = 11.sp,
                        color = TextDim
                    )
                }
                Text(
                    if (prospect.valueCents > 0) "$${"%.2f".format(prospect.valueCents / 100.0)}" else "no value set",
                    fontSize = 12.sp,
                    color = TextDim
                )
                if (prospect.notes.isNotBlank()) {
                    Text(prospect.notes.take(160), fontSize = 11.sp, color = TextDim)
                }
                val owner = state.agent(prospect.ownerAgentId)?.name
                if (owner != null) {
                    Text("owner: $owner", fontSize = 10.sp, color = TextDim)
                }
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

        val samples = state.deliverables.filter { it.isSample }.sortedByDescending { it.createdAt }
        item { SectionHeader("SAMPLES", "showcase work a buyer can see before paying") }
        if (samples.isEmpty()) {
            item { EmptyState("No samples yet. The showcase bot makes them for each offer.") }
        }
        items(samples.take(6)) { sample ->
            HiveCard(accent = Teal) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(sample.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(sample.kind.name.lowercase(), fontSize = 10.sp, color = TextDim)
                }
                if (sample.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(sample.body.take(240), fontSize = 11.sp, color = TextDim)
                }
            }
        }

        item {
            SectionHeader("SERVICES", "AI-run services the team delivers")
            if (state.services.isEmpty()) {
                EmptyState("No services listed yet.")
            }
            state.services.forEach { service ->
                HiveCard {
                    Text(service.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("$${"%.2f".format(service.priceCents / 100.0)}", fontSize = 11.sp, color = Good)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun stageColor(prospect: Prospect) = when (prospect.stage) {
    ProspectStage.WON -> Good
    ProspectStage.LOST -> TextDim
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
