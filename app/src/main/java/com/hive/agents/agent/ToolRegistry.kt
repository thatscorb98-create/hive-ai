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
import com.hive.agents.net.Http
import com.hive.agents.net.StripeApi
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

    val specs = listOf(
        ToolSpec(
            name = "remember",
            description = "Save something worth keeping: a preference, a rule, a fact, or a lesson after a mistake.",
            schemaJson = """
                {"type":"object","properties":{
                  "kind":{"type":"string","enum":["preference","rule","fact","lesson","worklog"]},
                  "text":{"type":"string","description":"What to remember, in one sentence"},
                  "context":{"type":"string","description":"For a lesson: the mistake this came from"}
                },"required":["kind","text"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "send_email",
            description = "Send an email from this agent's own address to a lead or customer.",
            schemaJson = """
                {"type":"object","properties":{
                  "to":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"}
                },"required":["to","subject","body"]}
            """.trimIndent(),
            risk = ToolRisk.ASK
        ),
        ToolSpec(
            name = "create_payment_link",
            description = "Create a Stripe payment link for a buyer who has agreed to pay. Never called ahead of time.",
            schemaJson = """
                {"type":"object","properties":{
                  "amount_cents":{"type":"integer"},"description":{"type":"string"},"customer_email":{"type":"string"}
                },"required":["amount_cents","description"]}
            """.trimIndent(),
            risk = ToolRisk.ASK
        ),
        ToolSpec(
            name = "save_deliverable",
            description = "Save finished work (website, image, copy, document, code) to the mission.",
            schemaJson = """
                {"type":"object","properties":{
                  "kind":{"type":"string","enum":["website","image","copy","document","code","other"]},
                  "title":{"type":"string"},"body":{"type":"string"}
                },"required":["kind","title","body"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "search_web",
            description = "Search the public web for research. Returns titles and links.",
            schemaJson = """
                {"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "add_prospect",
            description = "Add a lead to the sales pipeline.",
            schemaJson = """
                {"type":"object","properties":{
                  "company":{"type":"string"},"contact_name":{"type":"string"},
                  "email":{"type":"string"},"notes":{"type":"string"}
                },"required":["company"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "message_teammate",
            description = "Send a direct message to another bot on the team, to coordinate or hand off work.",
            schemaJson = """
                {"type":"object","properties":{
                  "to":{"type":"string","description":"Bot name or role"},
                  "message":{"type":"string"}
                },"required":["to","message"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "open_website",
            description = "Open a website in this bot's own browser.",
            schemaJson = """
                {"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "read_page",
            description = "Read the visible text of the page in this bot's browser.",
            schemaJson = """{"type":"object","properties":{}}""".trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "click_text",
            description = "Click the link or button on the current page whose label contains this text.",
            schemaJson = """
                {"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "type_into",
            description = "Type into the form field on the current page that matches this label or placeholder.",
            schemaJson = """
                {"type":"object","properties":{"target":{"type":"string"},"value":{"type":"string"}},"required":["target","value"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "create_product",
            description = "Create a digital product or AI-run service the business can sell.",
            schemaJson = """
                {"type":"object","properties":{
                  "name":{"type":"string"},"description":{"type":"string"},
                  "price_cents":{"type":"integer"},
                  "kind":{"type":"string","enum":["website","image","copy","document","code","other"]}
                },"required":["name","price_cents"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "create_checkout",
            description = "Create a Stripe checkout link for a product, only once a buyer is ready to pay.",
            schemaJson = """
                {"type":"object","properties":{"product":{"type":"string","description":"Product name"}},"required":["product"]}
            """.trimIndent(),
            risk = ToolRisk.ASK
        ),
        ToolSpec(
            name = "hire_agent",
            description = "Hire a new bot for the company. Use it to build the marketing, research, sales, product and proofreading teams.",
            schemaJson = """
                {"type":"object","properties":{
                  "name":{"type":"string","description":"Short bot name"},
                  "role":{"type":"string","description":"e.g. Sales, Research, Marketing, Product, Proofreader"},
                  "charter":{"type":"string","description":"One sentence on what this bot owns"}
                },"required":["role"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "add_leads",
            description = "Add several real businesses to the sales pipeline at once. Verify each one first; never invent contacts.",
            schemaJson = """
                {"type":"object","properties":{"leads":{"type":"array","items":{"type":"object","properties":{
                  "company":{"type":"string"},"contact_name":{"type":"string"},"email":{"type":"string"},
                  "notes":{"type":"string"},"value_cents":{"type":"integer"}
                },"required":["company"]}}},"required":["leads"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "record_sale",
            description = "Record a closed sale so company progress is measured by sales made.",
            schemaJson = """
                {"type":"object","properties":{
                  "company":{"type":"string"},"email":{"type":"string"},
                  "amount_cents":{"type":"integer"},"notes":{"type":"string"}
                },"required":["company","amount_cents"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "proofread",
            description = "Rewrite outbound copy so it sounds human and has no AI filler. Keeps every fact and price.",
            schemaJson = """
                {"type":"object","properties":{
                  "text":{"type":"string"},"what":{"type":"string","description":"e.g. email body, product description"}
                },"required":["text"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        ),
        ToolSpec(
            name = "fetch_page",
            description = "Download a web page and return its readable text, so you can research without opening a browser window.",
            schemaJson = """
                {"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}
            """.trimIndent(),
            risk = ToolRisk.AUTO
        )
    )

    suspend fun call(name: String, argsJson: String, agent: Agent): String {
        val args = runCatching { JSONObject(argsJson.ifBlank { "{}" }) }.getOrElse { JSONObject() }
        val spec = specs.firstOrNull { it.name == name } ?: return "Unknown tool: $name"
        val settings = store.snapshot().settings
        val needsApproval = when (name) {
            "send_email" -> settings.requireApprovalForEmail
            else -> spec.risk == ToolRisk.ASK
        }

        if (needsApproval) {
            val approved = approvals.request(
                ApprovalRequest(
                    agentId = agent.id,
                    agentName = agent.name,
                    tool = name,
                    title = approvalTitle(name, args),
                    detail = approvalDetail(name, args),
                    irreversible = name == "create_payment_link"
                )
            )
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

            "search_web" -> search(args.optString("query"))

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
                            updatedAt = now
                        )
                    } else {
                        state.prospects.map {
                            if (it.id == known.id) it.copy(
                                stage = ProspectStage.WON,
                                valueCents = if (cents > 0) cents else it.valueCents,
                                email = email.ifBlank { it.email },
                                notes = notes.ifBlank { it.notes },
                                updatedAt = now
                            ) else it
                        }
                    }
                    state.copy(
                        prospects = prospects,
                        messages = state.messages + Message(
                            agentId = agent.id,
                            kind = MessageKind.SYSTEM,
                            body = "Sale recorded: $company - $${"%.2f".format(cents / 100.0)}"
                        )
                    )
                }
                memory.remember(
                    agent.id,
                    MemoryKind.FACT,
                    "Sale: $company, $${"%.2f".format(cents / 100.0)}",
                    source = "sales"
                )
                "Recorded the sale for $company."
            }

            "proofread" -> proofreader.copy(
                args.optString("text"),
                args.optString("what").ifBlank { "outbound copy" }
            )

            "fetch_page" -> fetchPage(args.optString("url"))

            else -> "Unhandled tool: $name"
        }
    }

    private suspend fun fetchPage(url: String): String {
        if (url.isBlank()) return "No URL given."
        val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val raw = runCatching { Http.get(target) }.getOrElse { return "Fetch failed: ${it.message}" }
        val text = raw
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?s)<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
        return text.take(6000).ifBlank { "No readable text on that page." }
    }

    private suspend fun search(query: String): String {
        if (query.isBlank()) return "Empty query."
        val raw = runCatching {
            Http.get("https://html.duckduckgo.com/html/?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
        }.getOrElse { return "Search failed: ${it.message}" }

        val hits = Regex("""result__a[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(raw)
            .take(5)
            .map { match ->
                val link = match.groupValues[1]
                val title = match.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                "- $title ($link)"
            }
            .toList()

        return if (hits.isEmpty()) "No results for \"$query\"." else hits.joinToString("\n")
    }

    private fun approvalTitle(name: String, args: JSONObject): String = when (name) {
        "send_email" -> "Send email to ${args.optString("to")}"
        "create_payment_link" -> "Create a Stripe link for $${args.optLong("amount_cents") / 100.0}"
        "create_checkout" -> "Create a checkout link for ${args.optString("product")}"
        else -> "Run $name"
    }

    private fun approvalDetail(name: String, args: JSONObject): String = when (name) {
        "send_email" -> "Subject: ${args.optString("subject")}\n\n${args.optString("body").take(600)}"
        "create_payment_link" ->
            "Amount: $${args.optLong("amount_cents") / 100.0}\nFor: ${args.optString("description")}\n\n" +
                "Stripe links are created only when a buyer is ready to pay."
        "create_checkout" ->
            "Product: ${args.optString("product")}\n\n" +
                "A checkout link is created only when a buyer is ready to pay."
        else -> args.toString()
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
