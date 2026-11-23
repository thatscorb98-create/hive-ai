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
    onAddService: (String, String) -> Unit,
    onAddProduct: (String, String, String) -> Unit,
    onCheckout: (String) -> Unit
) {
    var company by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }
    var servicePrice by remember { mutableStateOf("") }
    var productName by remember { mutableStateOf("") }
    var productPrice by remember { mutableStateOf("") }
    var productPitch by remember { mutableStateOf("") }

    val wonCents = state.prospects.filter { it.stage == ProspectStage.WON }.sumOf { it.valueCents }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Business", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${state.settings.businessName} - collected $${"%.2f".format(wonCents / 100.0)}",
                    fontSize = 11.sp,
                    color = TextDim
                )
            }
        }

        item { SectionHeader("DIGITAL PRODUCTS", "what the bots sell, with a Stripe checkout when a buyer is ready") }

        if (state.products.isEmpty()) {
            item { EmptyState("No products yet. A bot can create one with the create_product tool.") }
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
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { onCheckout(product.id) }) {
                    Text("Create checkout link", fontSize = 11.sp, color = Amber)
                }
            }
        }

        item {
            SectionHeader("ADD PRODUCT", "a digital good or an AI-run service")
            LabeledField("Name", productName) { productName = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("What the buyer gets", productPitch) { productPitch = it }
            Spacer(Modifier.height(6.dp))
            LabeledField("Price (USD)", productPrice) { productPrice = it }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onAddProduct(productName, productPitch, productPrice)
                productName = ""; productPitch = ""; productPrice = ""
            }) { Text("Add product") }
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
                    if (prospect.valueCents > 0) "$${"%.2f".format(prospect.valueCents / 100.0)}" else "no value set",
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
            SectionHeader("SERVICES", "what else the business sells")
            if (state.services.isEmpty()) {
                EmptyState("No services listed.")
            }
            state.services.forEach { service ->
                HiveCard {
                    Text(service.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("$${"%.2f".format(service.priceCents / 100.0)}", fontSize = 11.sp, color = Good)
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
