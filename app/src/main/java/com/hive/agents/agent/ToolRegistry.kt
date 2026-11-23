package com.hive.agents.agent

import com.hive.agents.data.Agent
import com.hive.agents.data.Deliverable
import com.hive.agents.data.DeliverableKind
import com.hive.agents.data.HiveStore
import com.hive.agents.data.KeyVault
import com.hive.agents.data.MemoryKind
import com.hive.agents.data.Prospect
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
    private val stripe: StripeApi
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
        )
    )

    suspend fun call(name: String, argsJson: String, agent: Agent): String {
        val args = runCatching { JSONObject(argsJson.ifBlank { "{}" }) }.getOrElse { JSONObject() }
        val spec = specs.firstOrNull { it.name == name } ?: return "Unknown tool: $name"

        if (spec.risk == ToolRisk.ASK) {
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
                val to = args.optString("to")
                val subject = args.optString("subject")
                val body = args.optString("body")
                if (!vault.load().hasMail) {
                    "No AgentMail key configured, so the email was not sent."
                } else {
                    runCatching { mail.send(agent.email, agent.email, to, subject, body) }
                        .fold(
                            onSuccess = { sent ->
                                store.update { state ->
                                    state.copy(
                                        sentMail = state.sentMail + sent.copy(agentId = agent.id, from = agent.email)
                                    )
                                }
                                "Sent to $to."
                            },
                            onFailure = { "Email failed: ${it.message}" }
                        )
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
                val deliverable = Deliverable(
                    agentId = agent.id,
                    kind = kind,
                    title = args.optString("title", "Untitled"),
                    body = args.optString("body")
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

            else -> "Unhandled tool: $name"
        }
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
        else -> "Run $name"
    }

    private fun approvalDetail(name: String, args: JSONObject): String = when (name) {
        "send_email" -> "Subject: ${args.optString("subject")}\n\n${args.optString("body").take(600)}"
        "create_payment_link" ->
            "Amount: $${args.optLong("amount_cents") / 100.0}\nFor: ${args.optString("description")}\n\n" +
                "Stripe links are created only when a buyer is ready to pay."
        else -> args.toString()
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
