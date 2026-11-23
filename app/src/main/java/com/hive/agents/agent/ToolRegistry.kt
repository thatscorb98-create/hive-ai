package com.hive.agents.agent

import com.hive.agents.browser.BrowserHub
import com.hive.agents.data.Agent
import com.hive.agents.data.Deliverable
import com.hive.agents.data.DeliverableKind
import com.hive.agents.data.HiveStore
import com.hive.agents.data.KeyVault
import com.hive.agents.data.MemoryKind
import com.hive.agents.data.Message
import com.hive.agents.data.MessageKind
import com.hive.agents.data.Product
import com.hive.agents.data.Prospect
import com.hive.agents.data.ProspectStage
import com.hive.agents.net.AgentMailApi
import com.hive.agents.net.StripeApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

enum class ToolRisk { AUTO, ASK }

data class ToolSpec(
    val name: String,
    val description: String,
    val schemaJson: String,
    val risk: ToolRisk
) {
    fun toWire(): JSONObject = JSONObject()
        .put("type", "function")
        .put(
            "function",
            JSONObject()
                .put("name", name)
                .put("description", description)
                .put("parameters", JSONObject(schemaJson))
        )
}

/**
 * Every capability an agent has goes through here, with an explicit risk class.
 * ASK tools stop and wait for the human; they never fire on their own.
 */
class ToolRegistry(
    private val store: HiveStore,
    private val vault: KeyVault,
    private val memory: MemoryService,
    private val approvals: ApprovalBroker,
    private val mail: AgentMailApi,
    private val stripe: StripeApi,
    private val proofreader: Proofreader
) {

    /** Approval dialogs are one at a time; the API calls that follow still run in parallel. */
    private val approvalLock = Mutex()

    val specs = ToolSpecs.all

    suspend fun call(name: String, argsJson: String, agent: Agent): String {
        val args = runCatching { JSONObject(argsJson.ifBlank { "{}" }) }.getOrElse { JSONObject() }
        val spec = specs.firstOrNull { it.name == name } ?: return "Unknown tool: $name"
        val settings = store.snapshot().settings
        val needsApproval = when (name) {
            "send_email" -> settings.requireApprovalForEmail
            else -> spec.risk == ToolRisk.ASK
        }

        if (needsApproval) {
            val approved = approvalLock.withLock {
                approvals.request(
                    ApprovalRequest(
                        agentId = agent.id,
                        agentName = agent.name,
                        tool = name,
                        title = ToolApproval.title(name, args),
                        detail = ToolApproval.detail(name, args),
                        irreversible = name == "create_payment_link"
                    )
                )
            }
            if (!approved) return "DENIED: the human did not approve this action."
        }

        return when (name) {
            "remember" -> {
                val kind = when (args.optString("kind").lowercase()) {
                    "preference" -> MemoryKind.PREFERENCE
                    "rule" -> MemoryKind.RULE
                    "lesson" -> MemoryKind.LESSON
                    "worklog" -> MemoryKind.WORKLOG
                    else -> MemoryKind.FACT
                }
                memory.remember(agent.id, kind, args.optString("text"), args.optString("context"))
                "Remembered."
            }

            "send_email" -> {
                val to = args.optString("to").trim()
                val subject = args.optString("subject")
                val body = args.optString("body")
                when {
                    to.isBlank() -> "No recipient."
                    !vault.load().hasMail -> "No AgentMail key configured, so the email was not sent."
                    else -> {
                        // Every outbound message is proofread before it leaves the building.
                        val clean = proofreader.outbound(subject, body)
                        runCatching { mail.send(agent.email, agent.email, to, clean.first, clean.second) }
                            .fold(
                                onSuccess = { sent ->
                                    store.update { state ->
                                        state.copy(
                                            sentMail = state.sentMail + sent.copy(agentId = agent.id, from = agent.email),
                                            prospects = state.prospects.map {
                                                if (it.email.equals(to, true) && it.stage != ProspectStage.WON) {
                                                    it.copy(stage = ProspectStage.PITCHED, updatedAt = System.currentTimeMillis())
                                                } else it
                                            }
                                        )
                                    }
                                    "Sent to $to (proofread first)."
                                },
                                onFailure = { "Email failed: ${it.message}" }
                            )
                    }
                }
            }

            "create_payment_link" -> {
                val cents = args.optLong("amount_cents")
                val description = args.optString("description")
                val email = args.optString("customer_email")
                if (!vault.load().hasStripe) {
                    "No Stripe key configured, so no link was created."
                } else {
                    runCatching {
                        stripe.createPaymentLink(
                            amountCents = cents,
                            currency = store.snapshot().settings.currency,
                            description = description,
                            customerEmail = email
                        )
                    }.fold(
                        onSuccess = { url -> "Payment link for $description: $url" },
                        onFailure = { "Stripe failed: ${it.message}" }
                    )
                }
            }

            "save_deliverable" -> {
                val kind = when (args.optString("kind").lowercase()) {
                    "website" -> DeliverableKind.WEBSITE
                    "image" -> DeliverableKind.IMAGE
                    "copy" -> DeliverableKind.COPY
                    "document" -> DeliverableKind.DOCUMENT
                    "code" -> DeliverableKind.CODE
                    else -> DeliverableKind.OTHER
                }
                val rawBody = args.optString("body")
                val deliverable = Deliverable(
                    agentId = agent.id,
                    kind = kind,
                    title = args.optString("title", "Untitled"),
                    body = if (kind == DeliverableKind.COPY) {
                        proofreader.copy(rawBody, "sales copy")
                    } else {
                        rawBody
                    }
                )
                store.update { state -> state.copy(deliverables = state.deliverables + deliverable) }
                "Saved deliverable ${deliverable.title}."
            }

            "search_web" -> {
                val keys = vault.load()
                ToolWeb.search(args.optString("query"), keys.googleSearchKey, keys.googleSearchCx)
            }

            "add_prospect" -> {
                val prospect = Prospect(
                    company = args.optString("company"),
                    contactName = args.optString("contact_name"),
                    email = args.optString("email"),
                    notes = args.optString("notes"),
                    ownerAgentId = agent.id
                )
                store.update { state -> state.copy(prospects = state.prospects + prospect) }
                "Added ${prospect.company} to the pipeline."
            }

            "message_teammate" -> {
                val targetRaw = args.optString("to")
                val teammate = store.snapshot().agents.firstOrNull {
                    it.id != agent.id && (it.name.equals(targetRaw, ignoreCase = true) ||
                        it.role.equals(targetRaw, ignoreCase = true))
                }
                if (teammate == null) {
                    "No teammate called $targetRaw. Team: " +
                        store.snapshot().agents.filter { it.id != agent.id }.joinToString { it.name }
                } else {
                    store.update { state ->
                        state.copy(
                            messages = state.messages + Message(
                                agentId = agent.id,
                                toAgentId = teammate.id,
                                kind = MessageKind.DM,
                                body = args.optString("message")
                            )
                        )
                    }
                    "Messaged ${teammate.name}."
                }
            }

            "open_website" -> {
                val url = args.optString("url")
                if (BrowserHub.ownsWindow(agent.id)) {
                    BrowserHub.open(agent.id, url)
                } else {
                    BrowserHub.requestOpen(agent.id, agent.name, url)
                    "Your browser is not the open window, so I asked the human to open it at $url."
                }
            }

            "read_page" -> BrowserHub.readPage(agent.id)

            "click_text" -> BrowserHub.click(agent.id, args.optString("text"))

            "type_into" -> BrowserHub.typeInto(agent.id, args.optString("target"), args.optString("value"))

            "create_product" -> {
                val kind = when (args.optString("kind").lowercase()) {
                    "website" -> DeliverableKind.WEBSITE
                    "image" -> DeliverableKind.IMAGE
                    "copy" -> DeliverableKind.COPY
                    "document" -> DeliverableKind.DOCUMENT
                    "code" -> DeliverableKind.CODE
                    else -> DeliverableKind.OTHER
                }
                val product = Product(
                    name = args.optString("name").trim(),
                    description = proofreader.copy(args.optString("description"), "product description"),
                    priceCents = args.optLong("price_cents"),
                    kind = kind,
                    createdBy = agent.id
                )
                store.update { state -> state.copy(products = state.products + product) }
                "Created product \"${product.name}\" at $${product.priceCents / 100.0}."
            }

            "create_checkout" -> {
                val wanted = args.optString("product")
                val product = store.snapshot().products.firstOrNull {
                    it.name.equals(wanted, ignoreCase = true)
                }
                when {
                    product == null -> "No product called $wanted."
                    !vault.load().hasStripe -> "No Stripe key in the vault, so no checkout was created."
                    else -> runCatching {
                        stripe.createPaymentLink(
                            amountCents = product.priceCents,
                            currency = store.snapshot().settings.currency,
                            description = "${store.snapshot().settings.businessName} - ${product.name}"
                        )
                    }.fold(
                        onSuccess = { url ->
                            store.update { state ->
                                state.copy(products = state.products.map {
                                    if (it.id == product.id) it.copy(checkoutUrl = url, status = "on sale") else it
                                })
                            }
                            "Checkout link for ${product.name}: $url"
                        },
                        onFailure = { "Stripe failed: ${it.message}" }
                    )
                }
            }

            "hire_agent" -> {
                val role = args.optString("role").trim().ifBlank { "Specialist" }
                val name = args.optString("name").trim().ifBlank { role }
                val existing = store.snapshot().agents.firstOrNull { it.name.equals(name, true) }
                if (existing != null) {
                    "${existing.name} already works here as ${existing.role}."
                } else {
                    val newcomer = Agent(
                        name = name,
                        role = role,
                        charter = args.optString("charter").trim(),
                        hiredBy = agent.id
                    )
                    store.update { state ->
                        state.copy(
                            agents = state.agents + newcomer,
                            messages = state.messages + Message(
                                agentId = agent.id,
                                kind = MessageKind.SYSTEM,
                                body = "Hired ${newcomer.name} as ${newcomer.role}."
                            )
                        )
                    }
                    memory.remember(newcomer.id, MemoryKind.WORKLOG, "Hired as $role by ${agent.name}.", source = "hiring")
                    memory.remember(agent.id, MemoryKind.WORKLOG, "Hired ${newcomer.name} as $role.", source = "hiring")
                    "${newcomer.name} joined as $role. Message them with message_teammate."
                }
            }

            "add_leads" -> {
                val arr = args.optJSONArray("leads") ?: return "No leads array."
                val now = System.currentTimeMillis()
                val current = store.snapshot().prospects
                val fresh = mutableListOf<Prospect>()
                var duplicates = 0
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val company = item.optString("company").trim()
                    if (company.isBlank()) continue
                    val email = item.optString("email").trim()
                    val duplicate = (current + fresh).any { known ->
                        (email.isNotBlank() && known.email.equals(email, true)) ||
                            known.company.equals(company, true)
                    }
                    if (duplicate) {
                        duplicates++
                        continue
                    }
                    fresh += Prospect(
                        company = company,
                        contactName = item.optString("contact_name").trim(),
                        email = email,
                        notes = item.optString("notes").trim(),
                        valueCents = item.optLong("value_cents"),
                        ownerAgentId = agent.id,
                        updatedAt = now
                    )
                }
                if (fresh.isEmpty()) return "No new leads. $duplicates were already in the pipeline."
                store.update { state -> state.copy(prospects = state.prospects + fresh) }
                "Added ${fresh.size} leads. $duplicates duplicates skipped."
            }

            "record_sale" -> {
                val company = args.optString("company").trim()
                if (company.isBlank()) return "No customer given."
                val email = args.optString("email").trim()
                val cents = args.optLong("amount_cents")
                val notes = args.optString("notes").trim()
                val now = System.currentTimeMillis()
                store.update { state ->
                    val known = state.prospects.firstOrNull {
                        (email.isNotBlank() && it.email.equals(email, true)) ||
                            it.company.equals(company, true)
                    }
                    val prospects = if (known == null) {
                        state.prospects + Prospect(
                            company = company,
                            email = email,
                            stage = ProspectStage.WON,
                            notes = notes,
                            valueCents = cents,
                            ownerAgentId = agent.id,
                            paid = true,
                            updatedAt = now
                        )
                    } else {
                        state.prospects.map {
                            if (it.id == known.id) it.copy(
                                stage = ProspectStage.WON,
                                valueCents = if (cents > 0) cents else it.valueCents,
                                email = email.ifBlank { it.email },
                                notes = notes.ifBlank { it.notes },
                                paid = true,
                                updatedAt = now
                            ) else it
                        }
                    }
                    state.copy(
                        prospects = prospects,
                        messages = state.messages + Message(
                            agentId = agent.id,
                            kind = MessageKind.SYSTEM,
                            body = "Sale recorded: $company - $${\"%.2f\".format(cents / 100.0)}"
                        )
                    )
                }
                memory.remember(
                    agent.id,
                    MemoryKind.FACT,
                    "Sale: $company, $${\"%.2f\".format(cents / 100.0)}",
                    source = "sales"
                )
                "Recorded payment from $company. Deliver the work and mark it delivered when it is done."
            }

            "create_sample" -> {
                val title = args.optString("title").trim()
                if (title.isBlank()) return "No sample title."
                val raw = args.optString("body")
                if (raw.isBlank()) return "No sample body."
                val kind = deliverableKind(args.optString("kind"))
                val body = if (store.snapshot().settings.proofreadOutbound) {
                    proofreader.copy(raw, "customer sample")
                } else {
                    raw
                }
                store.update { state ->
                    state.copy(
                        deliverables = state.deliverables + Deliverable(
                            kind = kind,
                            title = title,
                            body = body,
                            agentId = agent.id,
                            isSample = true
                        )
                    )
                }
                "Saved the sample \"$title\" so buyers can see it."
            }

            "deliver_work" -> {
                val company = args.optString("company").trim()
                if (company.isBlank()) return "No customer given."
                val email = args.optString("email").trim()
                val title = args.optString("title").trim().ifBlank { "Delivered work for $company" }
                val raw = args.optString("body")
                if (raw.isBlank()) return "No delivered work given."
                val body = if (store.snapshot().settings.proofreadOutbound) {
                    proofreader.copy(raw, "customer deliverable")
                } else {
                    raw
                }
                var matched = false
                var wasPaid = false
                store.update { state ->
                    val known = state.prospects.firstOrNull {
                        (email.isNotBlank() && it.email.equals(email, true)) ||
                            it.company.equals(company, true)
                    } ?: return@update state
                    matched = true
                    wasPaid = known.paid
                    state.copy(
                        prospects = state.prospects.map {
                            if (it.id == known.id) it.copy(delivered = true, updatedAt = System.currentTimeMillis()) else it
                        },
                        deliverables = state.deliverables + Deliverable(
                            kind = deliverableKind(args.optString("kind")),
                            title = title,
                            body = body,
                            agentId = agent.id
                        )
                    )
                }
                if (!matched) return "No customer named $company in the pipeline."
                if (wasPaid) {
                    "Delivered $title to $company. The job is closed."
                } else {
                    "Delivered $title to $company. Payment is not recorded yet; record_sale when it lands."
                }
            }

            "proofread" -> proofreader.copy(
                args.optString("text"),
                args.optString("what").ifBlank { "outbound copy" }
            )

            "fetch_page" -> ToolWeb.fetchPage(args.optString("url"))

            else -> "Unhandled tool: $name"
        }
    }

    private fun deliverableKind(raw: String): DeliverableKind = when (raw.lowercase()) {
        "image" -> DeliverableKind.IMAGE
        "website" -> DeliverableKind.WEBSITE
        "document" -> DeliverableKind.DOCUMENT
        else -> DeliverableKind.COPY
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
