package com.hive.agents.agent

import com.hive.agents.data.Agent
import com.hive.agents.data.HiveState
import com.hive.agents.data.HiveStore
import com.hive.agents.data.MemoryKind
import com.hive.agents.data.Message
import com.hive.agents.data.MessageKind
import com.hive.agents.data.Mission
import com.hive.agents.data.MissionStatus
import com.hive.agents.data.ProspectStage
import com.hive.agents.data.Task
import com.hive.agents.data.TaskStatus
import com.hive.agents.net.NimClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import java.util.UUID

/**
 * The company lead and the work loop.
 *
 * The app starts with a single lead bot that knows it runs Local Reach Workshop. That bot hires
 * the marketing, research, sales, product and proofreading people it needs, then splits missions
 * across them. Every bot can talk to every other bot, and every bot can hire when the work needs
 * another pair of hands.
 */
class TeamEngine(
    private val store: HiveStore,
    private val nim: NimClient,
    private val tools: ToolRegistry,
    private val memory: MemoryService,
    private val suggestions: SuggestionBox
) {

    suspend fun hire(
        role: String,
        name: String,
        charter: String,
        by: String = "orchestrator"
    ): Agent {
        val cleanRole = role.trim().ifBlank { "Specialist" }
        val cleanName = name.trim().ifBlank { cleanRole }
        val existing = store.snapshot().agents.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
        if (existing != null) return existing

        val agent = Agent(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            role = cleanRole,
            charter = charter.trim(),
            hiredBy = by
        )
        store.update { state -> state.copy(agents = state.agents + agent) }
        memory.remember(
            agent.id,
            MemoryKind.WORKLOG,
            "Hired as $cleanRole. Charter: ${charter.trim()}",
            source = "hiring"
        )
        say(agent, "I'm on the team as $cleanRole. ${charter.trim()}", kind = MessageKind.SYSTEM)
        return agent
    }

    /**
     * A human talking to a bot is a real turn, not a chat-only reply: the bot can hire, add
     * leads, create a product or proofread something while answering.
     */
    suspend fun converse(agent: Agent, text: String): String {
        val messages = mutableListOf(
            NimClient.Msg("system", systemPrompt(agent)),
            NimClient.Msg(
                "user",
                buildString {
                    appendLine(text)
                    appendLine()
                    appendLine("If this needs a tool, call it now. Then reply with what you did, in one short paragraph.")
                }
            )
        )

        val model = ModelRouter.forAgent(agent, store.snapshot().settings)
        var last = ""
        repeat(6) {
            val reply = nim.chat(messages, tools.specs, model = model)
            if (reply.content.isNotBlank()) last = reply.content
            if (reply.calls.isEmpty()) return last.ifBlank { "..." }

            messages += NimClient.Msg("assistant", reply.content.ifBlank { "Calling tools." })
            for (call in reply.calls) {
                val result = tools.call(call.name, call.args, agent)
                messages += NimClient.Msg("user", "Result of ${call.name}: $result")
            }
        }
        return last.ifBlank { "Stopped after six tool rounds." }
    }

    suspend fun runMission(missionId: String) {
        val start = store.snapshot()
        val mission = start.missions.firstOrNull { it.id == missionId } ?: return

        store.update { it.copy(settings = it.settings.copy(lastShiftAt = System.currentTimeMillis())) }
        setMission(missionId, MissionStatus.RUNNING, "")
        say(start.orchestrator, "Starting \"${mission.title}\".", missionId, kind = MessageKind.SYSTEM)

        val team = ensureTeam()
        val planned = planTasks(mission, team)
        store.update { s -> s.copy(tasks = s.tasks + planned) }

        // Every task is dispatched at the same time, so research, outreach, product work and
        // proofreading overlap instead of waiting in a queue. Each bot calls its own model.
        val results = coroutineScope {
            planned.map { task ->
                async(Dispatchers.IO) { executeTask(task, mission, team) }
            }.awaitAll()
        }
        val failed = results.any { !it }

        val summary = store.snapshot().tasks.filter { it.missionId == missionId }
            .joinToString("\n") { "- ${it.title}: ${it.status.name.lowercase()}" }

        setMission(missionId, if (failed) MissionStatus.BLOCKED else MissionStatus.DONE, summary)
        say(
            store.snapshot().orchestrator,
            if (failed) "Mission paused for review.\n$summary" else "Mission complete.\n$summary",
            missionId,
            kind = MessageKind.SYSTEM
        )
    }

    fun say(
        agent: Agent?,
        text: String,
        missionId: String = "",
        toAgentId: String = "",
        kind: MessageKind = MessageKind.CHAT,
        fromUser: Boolean = false
    ) {
        if (text.isBlank()) return
        val message = Message(
            id = UUID.randomUUID().toString(),
            agentId = agent?.id.orEmpty(),
            missionId = missionId,
            toAgentId = toAgentId,
            kind = kind,
            body = text.trim(),
            fromUser = fromUser
        )
        store.update { state -> state.copy(messages = state.messages + message) }
    }

    /** Direct message between two bots, so they coordinate without filling the human's thread. */
    fun dm(from: Agent, to: Agent, text: String, missionId: String = "") {
        say(from, text, missionId, toAgentId = to.id, kind = MessageKind.DM)
    }

    fun handoff(from: Agent, to: Agent, task: Task, missionId: String) {
        say(
            from,
            "Handing \"${task.title}\" to ${to.name}.",
            missionId,
            toAgentId = to.id,
            kind = MessageKind.HANDOFF
        )
        updateTask(task.id) { it.copy(assigneeId = to.id) }
    }

    /** The company never auto-hires three strangers: the lead builds the team it needs. */
    private fun ensureTeam(): List<Agent> {
        if (store.snapshot().agents.isEmpty()) bootstrap()
        return store.snapshot().agents
    }

    private suspend fun planTasks(mission: Mission, team: List<Agent>): List<Task> {
        if (team.size <= 1) {
            return listOf(
                Task(
                    id = UUID.randomUUID().toString(),
                    missionId = mission.id,
                    title = mission.title,
                    detail = mission.brief,
                    assigneeId = team.firstOrNull()?.id.orEmpty()
                )
            )
        }

        val roster = team.joinToString("\n") { "- ${it.name} :: ${it.role}" }
        val prompt = """
            Mission: ${mission.title}
            Brief: ${mission.brief}
            Team:
            $roster

            Break the mission into 2-4 concrete tasks that can run in parallel. Reply with a JSON array only, each item:
            {"title":"short task","detail":"what done looks like","role":"role from the team"}
        """.trimIndent()

        val lead = team.firstOrNull { it.primary }
        val model = lead?.let { ModelRouter.forAgent(it, store.snapshot().settings) }
            ?: store.snapshot().settings.model
        val reply = nim.chat(listOf(NimClient.Msg("system", prompt)), model = model, maxTokens = 500)
        val parsed = parseTasks(reply.content, team).map { it.copy(missionId = mission.id) }
        if (parsed.isNotEmpty()) return parsed

        val fallbackAgent = team.firstOrNull { !it.primary } ?: team.first()
        return listOf(
            Task(
                id = UUID.randomUUID().toString(),
                missionId = mission.id,
                title = mission.title,
                detail = mission.brief,
                assigneeId = fallbackAgent?.id.orEmpty()
            )
        )
    }

    private suspend fun executeTask(task: Task, mission: Mission, team: List<Agent>): Boolean {
        val agent = store.snapshot().agent(task.assigneeId)
            ?: team.firstOrNull { !it.primary }
            ?: team.firstOrNull()
            ?: return false

        updateTask(task.id) { it.copy(assigneeId = agent.id, status = TaskStatus.RUNNING) }
        say(agent, "On it: ${task.title}", mission.id, kind = MessageKind.SYSTEM)

        return runCatching { dispatch(agent, task, mission) }
            .fold(
                onSuccess = { result ->
                    updateTask(task.id) { it.copy(status = TaskStatus.DONE, output = result) }
                    say(agent, result, mission.id)
                    true
                },
                onFailure = { error ->
                    val message = error.message ?: "unknown error"
                    updateTask(task.id) { it.copy(status = TaskStatus.FAILED, output = message) }
                    memory.lesson(
                        agent.id,
                        "Task \"${task.title}\" failed: $message",
                        "Read the tool output before retrying, and never repeat a call that already failed the same way."
                    )
                    suggestions.fromFailure(agent, task.title, message)
                    say(agent, "Blocked: $message", mission.id, kind = MessageKind.SYSTEM)
                    false
                }
            )
    }

    private suspend fun dispatch(agent: Agent, task: Task, mission: Mission): String {
        val building = store.snapshot().agents.size <= 1
        val model = ModelRouter.forAgent(agent, store.snapshot().settings)
        val messages = mutableListOf(
            NimClient.Msg("system", systemPrompt(agent)),
            NimClient.Msg(
                "user",
                buildString {
                    appendLine("Mission: ${mission.title}")
                    appendLine("Brief: ${mission.brief}")
                    appendLine("Your task: ${task.title}")
                    if (task.detail.isNotBlank()) appendLine(task.detail)
                    if (building) {
                        appendLine()
                        appendLine("You are the only bot right now. Before doing the work, hire the people this company needs with hire_agent: marketing, research, sales, product, a showcase bot and a proofreader. You can hire more than one person per function.")
                    }
                    val guard = memory.guardrails(agent.id, "${mission.title} ${task.title}")
                    if (guard.isNotBlank()) {
                        appendLine()
                        appendLine(guard)
                    }
                    appendLine()
                    appendLine("Use tools when they help. Finish with a short report of what you did, and record any sale with record_sale.")
                }
            )
        )

        var last = ""
        repeat(5) {
            val reply = nim.chat(messages, tools.specs, model = model)
            if (reply.content.isNotBlank()) last = reply.content
            if (reply.calls.isEmpty()) return last.ifBlank { "Done." }

            messages += NimClient.Msg("assistant", reply.content.ifBlank { "Calling tools." })
            for (call in reply.calls) {
                val result = tools.call(call.name, call.args, agent)
                messages += NimClient.Msg("user", "Result of ${call.name}: $result")
            }
        }
        return last.ifBlank { "Stopped after five tool rounds." }
    }

    private fun systemPrompt(agent: Agent): String {
        val state = store.snapshot()
        val facts = memory.sharedFacts().joinToString("\n") { "- ${it.text}" }
        val products = state.products.take(8).joinToString("\n") { "- ${it.name} ($${it.priceCents / 100.0})" }
        val pipeline = pipelineSnapshot(state)
        val team = state.agents.joinToString("\n") { "- ${it.name} :: ${it.role}" }
        return buildString {
            appendLine("You work for ${state.settings.businessName}, a registered business that sells digital goods and AI-run services.")
            appendLine("You are ${agent.name}, the ${agent.role}, and you are part of an autonomous team that earns money for the company.")
            appendLine("Your charter: ${agent.charter}")
            appendLine("What the business sells: ${state.settings.buyingGuide}")
            if (agent.primary) {
                appendLine()
                appendLine("You are running ${state.settings.businessName}. You are the boss: you decide what to sell, who to hire and how the company makes money.")
                appendLine("You started alone. Build the company with hire_agent: a marketing team, a research team, a sales team, a product team, a showcase bot and a proofreader. Hire more people whenever the work needs them.")
            }
            appendLine("The company runs a continuous loop: market research, lead lists, qualification, outreach, follow-up, payment, then delivery. Keep moving to the next stage without waiting to be asked.")
            appendLine("Once a buyer has paid, do the work, save it, and mark the job delivered with deliver_work. Never leave a paid customer waiting.")
            appendLine("The showcase bot turns what the company sells into samples buyers can see. Use create_sample for every reusable sample.")
            if (products.isNotBlank()) {
                appendLine("Current products:")
                appendLine(products)
            }
            if (pipeline.isNotBlank()) {
                appendLine()
                appendLine(pipeline)
            }
            if (team.isNotBlank()) {
                appendLine("The team:")
                appendLine(team)
            }
            appendLine("Payments run through the company's Stripe account, and a payment link is only created when a buyer is ready to pay.")
            appendLine("Sales work means real businesses: build lead lists with add_leads, use search_web and fetch_page to check them, email them with send_email, and record every closed deal with record_sale. Never invent a contact or an address.")
            appendLine("Every outbound email and every product description is proofread before it goes out. Write plain, specific copy and let the proofreader clean it; never pad a message with AI filler.")
            appendLine("Progress is judged by sales actually made, not by how busy the team looks.")
            if (facts.isNotBlank()) {
                appendLine("Business facts:")
                appendLine(facts)
            }
            appendLine("You share a team chat with the other bots, and you can message any of them with message_teammate. When another bot has to continue your work, message them the handoff before you finish.")
            appendLine("Be concrete, use tools, never invent results. A payment link or an email that costs money still needs human approval if the human has asked for it.")
        }
    }

    /** The live funnel every bot needs before it can research, sell or deliver anything. */
    private fun pipelineSnapshot(state: HiveState): String {
        val open = state.prospects
            .filter { it.stage != ProspectStage.LOST }
            .sortedByDescending { it.updatedAt }
            .take(20)
        val samples = state.deliverables.filter { it.isSample }.take(5)
        if (open.isEmpty() && samples.isEmpty()) return ""
        return buildString {
            appendLine("Live pipeline:")
            open.forEach { p ->
                appendLine(
                    "- ${p.company} | ${p.stage.name.lowercase()} | email: ${p.email.ifBlank { "missing" }} | " +
                        "paid: ${p.paid} | delivered: ${p.delivered} | value: ${p.valueCents / 100.0} | ${p.notes.take(70)}"
                )
            }
            if (samples.isNotEmpty()) {
                appendLine("Samples ready: ${samples.joinToString { it.title }}")
            }
            appendLine("Sales made: ${state.salesMade}; collected: ${state.revenueCents / 100.0}")
        }
    }

    private fun parseTasks(raw: String, team: List<Agent>): List<Task> {
        val json = extractJsonArray(raw) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                val role = item.optString("role")
                val agent = team.firstOrNull { it.role.equals(role, ignoreCase = true) }
                    ?: team.firstOrNull { !it.primary }
                    ?: team.first()
                Task(
                    id = UUID.randomUUID().toString(),
                    missionId = "",
                    title = item.optString("title").ifBlank { "Task ${i + 1}" },
                    detail = item.optString("detail"),
                    assigneeId = agent?.id.orEmpty()
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun extractJsonArray(raw: String): String? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    private fun setMission(id: String, status: MissionStatus, result: String) {
        store.update { state ->
            state.copy(missions = state.missions.map {
                if (it.id == id) it.copy(status = status, result = result) else it
            })
        }
    }

    private fun updateTask(id: String, block: (Task) -> Task) {
        store.update { state ->
            state.copy(tasks = state.tasks.map { if (it.id == id) block(it) else it })
        }
    }

    /** Called once on first launch. One lead bot, and it knows what company it runs. */
    fun bootstrap() {
        val state = store.snapshot()
        if (state.agents.isEmpty()) {
            store.update { current -> current.copy(agents = listOf(newLead())) }
        } else if (state.agents.none { it.primary }) {
            val first = state.agents.first()
            store.update { current ->
                current.copy(agents = current.agents.map {
                    if (it.id == first.id) it.copy(primary = true) else it
                })
            }
        }

        // Autopilot used to default to off, and the old version auto-hired a trio before the lead
        // could choose. Do both once, then respect the human's setup.
        if (!state.settings.teamCleaned) {
            val kept = state.agents.filter { it.primary || it.hiredBy != "orchestrator" }
            val team = if (kept.isEmpty()) listOf(newLead()) else kept
            store.update {
                it.copy(
                    agents = team,
                    settings = it.settings.copy(
                        teamCleaned = true,
                        autopilot = true,
                        autopilotDefaulted = true,
                        requireApprovalForEmail = false
                    )
                )
            }
        } else if (!state.settings.autopilotDefaulted) {
            store.update {
                it.copy(settings = it.settings.copy(autopilot = true, autopilotDefaulted = true))
            }
        }
        seedBusinessFacts()
    }

    private fun newLead(): Agent = Agent(
        name = "Chief",
        role = "Founder",
        charter = "Runs Local Reach Workshop. Knows the company sells digital goods and AI-run services, builds the marketing, research, sales and product teams, and is judged on sales made.",
        primary = true,
        hiredBy = "human"
    )

    /** Facts every bot shares, so no agent has to be told who it works for. */
    private fun seedBusinessFacts() {
        val state = store.snapshot()
        val name = state.settings.businessName
        val needed = listOf(
            "$name is a registered business that sells digital goods and AI-run services to local businesses.",
            "The AI team runs $name day to day. The lead bot builds a marketing team, a research team, a sales team and a product team, and can hire more people whenever work piles up.",
            "The company gets paid through the human's Stripe account. A payment link is created only when a customer is ready to buy, never before.",
            "Sales bots build lead lists of real businesses, verify contact details, contact them, and record every closed deal with record_sale. No invented contacts, results or claims.",
            "A proofreader reviews every outbound email and product description before it is sent or published, so the writing sounds human.",
            "The company runs a continuous loop: research real leads, decide what each one needs, contact them, close the sale, then deliver the work after payment. A showcase bot makes samples of the work so buyers can see it before they pay.",
            "The team works in parallel and routes work to different models by role: the lead and product bot use the strong model, research and sales use the fast model, the proofreader uses the cheap model.",
            "Once a customer has paid, do the work, save it, and mark the job delivered with deliver_work. A paying customer comes before a new lead.",
            "Progress is measured by sales actually made and money collected, not by how busy the bots look.",
            "Each bot has its own browser window and can ask the human to sign in when a site needs it.",
            "The company's EIN and API keys live on this device. Never print, email, share or repeat them."
        )
        needed.forEach { fact ->
            val exists = state.memory.any { it.agentId == "*" && it.text.equals(fact, ignoreCase = true) }
            if (!exists) memory.remember("*", MemoryKind.FACT, fact, source = "business")
        }
    }
}
