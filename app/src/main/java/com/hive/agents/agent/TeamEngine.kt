package com.hive.agents.agent

import com.hive.agents.data.Agent
import com.hive.agents.data.HiveState
import com.hive.agents.data.HiveStore
import com.hive.agents.data.MemoryKind
import com.hive.agents.data.Message
import com.hive.agents.data.MessageKind
import com.hive.agents.data.Mission
import com.hive.agents.data.MissionStatus
import com.hive.agents.data.Models
import com.hive.agents.data.ProspectStage
import com.hive.agents.data.Task
import com.hive.agents.data.TaskStatus
import com.hive.agents.net.NimClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val crewMutex = Mutex()

    /** One shift is capped so a free model tier is not hammered by seven agents at once. */
    private companion object {
        const val MAX_TASKS = 5
    }

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
        repeat(8) {
            val reply = nim.chat(messages, tools.specs, model = model)
            if (reply.content.isNotBlank()) last = reply.content
            if (reply.calls.isEmpty()) return last.ifBlank { "..." }

            // Echo the assistant's real tool calls, then answer each one with a tool result.
            // Sending "Calling tools." and a user message instead made models repeat calls.
            messages += NimClient.Msg("assistant", reply.content, toolCalls = reply.calls)
            for (call in reply.calls) {
                val result = tools.call(call.name, call.args, agent).ifBlank { "No output." }
                messages += NimClient.Msg("tool", result, toolCallId = call.id)
            }
        }
        return wrapUp(
            model,
            messages,
            last.ifBlank { "I stopped after eight tool rounds without a final reply." }
        )
    }

    suspend fun runMission(missionId: String) {
        val mission = store.snapshot().missions.firstOrNull { it.id == missionId } ?: return
        say(store.snapshot().orchestrator, "Starting \"${mission.title}\".", missionId, kind = MessageKind.SYSTEM)
        val healthy = executeMission(mission)
        finishMission(mission, healthy)
    }

    /**
     * The owner typing to the boss is a work order, not a chat reply. Turn it into a mission,
     * give every bot its part, and answer with what the team actually did. This is the path that
     * used to stop after the boss's first tool call.
     */
    suspend fun takeOrder(agent: Agent, text: String): String {
        val mission = Mission(title = orderTitle(text), brief = text.trim(), ownerAgentId = agent.id)
        store.update { it.copy(missions = it.missions + mission) }
        say(
            agent,
            "On it: \"${mission.title}\". Splitting this across the team now.",
            mission.id,
            kind = MessageKind.SYSTEM
        )

        val healthy = executeMission(mission)
        val summary = finishMission(mission, healthy)
        val tasks = store.snapshot().tasks.filter { it.missionId == mission.id }
        val done = tasks.count { it.status == TaskStatus.DONE }
        val blocked = tasks.count { it.status == TaskStatus.FAILED }

        return buildString {
            appendLine(
                if (healthy) "Done. $done of ${tasks.size} bot job(s) finished."
                else "Ran the shift: $done finished, $blocked blocked. The thread has each bot's report."
            )
            append(summary)
        }
    }

    /**
     * Plan, then run the work in waves: context first (plan, research, offer, sample), then
     * selling and building, then proofreading. Bots inside a wave still run at the same time,
     * and each wave receives the finished work of the wave before it, so the team hands off
     * instead of every bot starting from nothing.
     */
    private suspend fun executeMission(mission: Mission): Boolean {
        setMission(mission.id, MissionStatus.RUNNING, "")
        store.update { it.copy(settings = it.settings.copy(lastShiftAt = System.currentTimeMillis())) }

        val team = ensureTeam()
        val planned = planTasks(mission, team)
        if (planned.isEmpty()) return false
        store.update { s -> s.copy(tasks = s.tasks + planned) }
        announcePlan(mission, planned, team)

        var failed = false
        planned
            .groupBy { task -> waveFor(team.firstOrNull { it.id == task.assigneeId }) }
            .toSortedMap()
            .forEach { (_, wave) ->
                val handoff = completedWork(mission.id)
                val results = coroutineScope {
                    wave.map { task ->
                        async(Dispatchers.IO) { executeTask(task, mission, team, handoff) }
                    }.awaitAll()
                }
                if (results.any { !it }) failed = true
            }

        // If the lead model failed, or simply never hired anyone, the company would stay a
        // one-bot chat forever. Give the lead a deterministic onboarding pass once. It is local
        // (no API calls), so a bad model can never leave the app with no crew again.
        if (store.snapshot().agents.size <= 1) ensureCoreTeam()
        return !failed
    }

    /** The owner has to see the boss handing each bot a job, in the chat, before work starts. */
    private fun announcePlan(mission: Mission, planned: List<Task>, team: List<Agent>) {
        val lead = team.firstOrNull { it.primary } ?: return
        val lines = planned.joinToString("\n") { task ->
            val who = team.firstOrNull { it.id == task.assigneeId }?.name ?: "someone"
            "- $who: ${task.title}"
        }
        say(lead, "Shift plan, ${planned.size} bots working:\n$lines", mission.id, kind = MessageKind.SYSTEM)
        planned.forEach { task ->
            val who = team.firstOrNull { it.id == task.assigneeId } ?: return@forEach
            if (who.id != lead.id) {
                dm(lead, who, "Your job this shift: ${task.title}. ${task.detail.take(220)}", mission.id)
            }
        }
    }

    /** Work already finished this shift, so the next wave builds on it instead of repeating it. */
    private fun completedWork(missionId: String): List<String> =
        store.snapshot().tasks
            .filter { it.missionId == missionId && it.status == TaskStatus.DONE && it.output.isNotBlank() }
            .takeLast(6)
            .map { task ->
                val who = store.snapshot().agent(task.assigneeId)?.name ?: "A teammate"
                "- $who on \"${task.title}\": ${task.output.take(600)}"
            }

    private fun summaryOf(missionId: String): String = store.snapshot().tasks
        .filter { it.missionId == missionId }
        .joinToString("\n") { task ->
            val who = store.snapshot().agent(task.assigneeId)?.name ?: "unassigned"
            "- $who: ${task.title} - ${task.status.name.lowercase()}"
        }

    private fun finishMission(mission: Mission, healthy: Boolean): String {
        val summary = summaryOf(mission.id)
        setMission(mission.id, if (healthy) MissionStatus.DONE else MissionStatus.BLOCKED, summary)
        say(
            store.snapshot().orchestrator,
            if (healthy) "Mission complete.\n$summary" else "Mission paused for review.\n$summary",
            mission.id,
            kind = MessageKind.SYSTEM
        )
        return summary
    }

    /** The first sentence of an order, short enough for a mission title. */
    private fun orderTitle(text: String): String {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        val first = clean.substringBefore('.').substringBefore('\n').ifBlank { clean }
        return first.take(70).trim().ifBlank { "Owner request" }
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

    /**
     * The lead plans. If the model answers with prose, an empty reply, or a plan that leaves
     * people idle, the standing role plan fills in, so a shift never collapses into one task
     * for one bot. That collapse is what made the company look like Chief and Scout only.
     */
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

            You are the boss. Give the work to the whole team, not to yourself alone. Reply with a JSON array only, each item:
            {"title":"short task","detail":"what done looks like","role":"role from the team"}
            Every bot you leave out sits idle this shift, so list one item per bot that has real work.
        """.trimIndent()

        val lead = team.firstOrNull { it.primary }
        val model = lead?.let { ModelRouter.forAgent(it, store.snapshot().settings) }
            ?: store.snapshot().settings.model
        val reply = try {
            // Reasoning tokens count against max_tokens. 500 was routinely spent on hidden
            // reasoning, producing HTTP 200 with empty content and a silent fallback task.
            nim.chat(
                listOf(NimClient.Msg("system", prompt)),
                model = model,
                maxTokens = 3000,
                temperature = 0.2
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            say(
                lead,
                "Planning call failed: ${error.message?.take(160) ?: "unknown error"}. " +
                    "Using the standing team plan so every bot still gets a job.",
                mission.id,
                kind = MessageKind.SYSTEM
            )
            return rosterPlan(mission, team)
        }

        val parsed = parseTasks(reply.content, team).map { it.copy(missionId = mission.id) }
        if (parsed.isEmpty()) {
            say(
                lead,
                "The model did not return a usable plan, so I'm assigning the standing team jobs instead.",
                mission.id,
                kind = MessageKind.SYSTEM
            )
            return rosterPlan(mission, team)
        }
        return mergePlan(parsed, mission, team)
    }

    /**
     * A readable plan can still leave half the crew idle. Fill the gaps with the standing role
     * brief for the bots the model forgot, capped so a free tier stays affordable.
     */
    private fun mergePlan(parsed: List<Task>, mission: Mission, team: List<Agent>): List<Task> {
        val used = parsed.map { it.assigneeId }.toSet()
        val spare = rosterPlan(mission, team).filter { it.assigneeId !in used }
        return (parsed + spare).take(MAX_TASKS)
    }

    /**
     * The standing plan: the stage of the business decides who must work, and the bots who have
     * waited longest fill the rest. The boss always gets a coordination job.
     */
    private fun rosterPlan(mission: Mission, team: List<Agent>): List<Task> {
        val lead = team.firstOrNull { it.primary }
        val specialists = team.filter { !it.primary }
        val lastWorked = store.snapshot().tasks.associate { it.assigneeId to it.createdAt }
        val picked = LinkedHashSet<Agent>()

        focusRoles(mission).forEach { key ->
            specialists.firstOrNull { matchesRole(it, key) }?.let { picked += it }
        }
        specialists.sortedBy { lastWorked[it.id] ?: 0L }.forEach {
            if (picked.size < MAX_TASKS - 1) picked += it
        }
        lead?.let { picked += it }

        return picked.take(MAX_TASKS).map { agent ->
            Task(
                id = UUID.randomUUID().toString(),
                missionId = mission.id,
                title = taskTitle(agent, mission),
                detail = roleBrief(agent, mission),
                assigneeId = agent.id
            )
        }
    }

    /** Which specialists this stage needs, most important first. */
    private fun focusRoles(mission: Mission): List<String> {
        val text = "${mission.title} ${mission.brief}".lowercase()
        return when {
            text.contains("paid") || text.contains("deliver") -> listOf("product", "sales", "showcase")
            text.contains("sample") -> listOf("showcase", "product", "market")
            text.contains("lead") || text.contains("research") || text.contains("contact") ->
                listOf("research", "sales")
            text.contains("sell") || text.contains("offer") || text.contains("product") ->
                listOf("market", "product", "showcase")
            text.contains("follow") || text.contains("close") -> listOf("sales", "research")
            else -> listOf("research", "market", "sales")
        }
    }

    private fun matchesRole(agent: Agent, key: String): Boolean =
        agent.role.lowercase().contains(key) || agent.name.lowercase().contains(key)

    private fun taskTitle(agent: Agent, mission: Mission): String {
        val role = agent.role.lowercase()
        return when {
            agent.primary -> "Plan the shift and brief the team"
            role.contains("research") -> "Find and verify real leads"
            role.contains("sales") -> "Pitch the leads that are ready"
            role.contains("market") -> "Package the offer"
            role.contains("product") -> "Build the deliverable"
            role.contains("showcase") -> "Make a buyer-ready sample"
            role.contains("proof") -> "Proofread what goes out"
            else -> "${agent.role}: ${mission.title}"
        }
    }

    /** A concrete, role-shaped job, so a bot never has to guess what it is supposed to do. */
    private fun roleBrief(agent: Agent, mission: Mission): String {
        val role = agent.role.lowercase()
        val job = when {
            agent.primary ->
                "Run this shift as the boss. Check the pipeline, hire anyone missing with hire_agent, " +
                    "message each teammate with their job using message_teammate, and decide what the " +
                    "company sells next. Do not do the specialists' work yourself."
            role.contains("research") ->
                "Find 5-10 real local businesses that fit the offer. Use search_web and fetch_page, then " +
                    "add the best ones with add_leads. Only record an email you actually saw on their site."
            role.contains("sales") ->
                "Work the leads that already have an email. Write one short, specific email per lead, " +
                    "send it with send_email, and record anything closed with record_sale."
            role.contains("market") ->
                "Package one offer: what the buyer gets, what it costs, and the outcome. Save the copy " +
                    "with save_deliverable and add the product with create_product if it is missing."
            role.contains("product") ->
                "Build the actual asset for the current offer or paid job and save it with save_deliverable. " +
                    "If a customer has paid, finish their work and close it with deliver_work."
            role.contains("showcase") ->
                "Make one or two samples a buyer can judge before paying, with create_sample."
            role.contains("proof") ->
                "Proofread the last outbound email and product copy with proofread, then save the cleaned version."
            else ->
                "Do the part of \"${mission.title}\" that fits your role, then report exactly what you did."
        }
        return buildString {
            appendLine("Mission: ${mission.title}")
            if (mission.brief.isNotBlank()) appendLine("Owner's brief: ${mission.brief}")
            appendLine(job)
        }.trim()
    }

    /** 0 context (plan, research, offer, sample), 1 selling and building, 2 proofreading. */
    private fun waveFor(agent: Agent?): Int {
        val role = agent?.role?.lowercase().orEmpty()
        return when {
            agent == null -> 0
            agent.primary -> 0
            role.contains("proof") -> 2
            role.contains("sales") || role.contains("product") -> 1
            else -> 0
        }
    }

    private suspend fun executeTask(
        task: Task,
        mission: Mission,
        team: List<Agent>,
        handoff: List<String>
    ): Boolean {
        val agent = store.snapshot().agent(task.assigneeId)
            ?: team.firstOrNull { !it.primary }
            ?: team.firstOrNull()
            ?: return false

        updateTask(task.id) { it.copy(assigneeId = agent.id, status = TaskStatus.RUNNING) }

        // Put the assignment in the transcript as an actual bot-to-bot message. The human can
        // open either bot and see the handoff instead of a flat activity log.
        val lead = team.firstOrNull { it.primary }
        val toLead = lead?.id?.takeIf { it != agent.id }.orEmpty()
        if (toLead.isNotBlank()) {
            say(
                lead,
                "Assigning \"${task.title}\" to ${agent.name}. ${task.detail.take(160)}",
                mission.id,
                toAgentId = agent.id,
                kind = MessageKind.HANDOFF
            )
            say(agent, "On it: ${task.title}", mission.id, toAgentId = toLead, kind = MessageKind.HANDOFF)
        } else {
            say(agent, "On it: ${task.title}", mission.id, kind = MessageKind.SYSTEM)
        }

        return runCatching { dispatch(agent, task, mission, handoff) }
            .fold(
                onSuccess = { result ->
                    updateTask(task.id) { it.copy(status = TaskStatus.DONE, output = result) }
                    say(
                        agent,
                        result,
                        mission.id,
                        toAgentId = toLead,
                        kind = if (toLead.isNotBlank()) MessageKind.HANDOFF else MessageKind.CHAT
                    )
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
                    say(
                        agent,
                        "Blocked: $message",
                        mission.id,
                        toAgentId = toLead,
                        kind = if (toLead.isNotBlank()) MessageKind.HANDOFF else MessageKind.SYSTEM
                    )
                    false
                }
            )
    }

    private suspend fun dispatch(
        agent: Agent,
        task: Task,
        mission: Mission,
        handoff: List<String> = emptyList()
    ): String {
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
                    if (handoff.isNotEmpty()) {
                        appendLine()
                        appendLine("Your teammates already finished this in the same shift. Build on it, do not repeat it:")
                        handoff.forEach { appendLine(it) }
                    }
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
        repeat(8) {
            val reply = nim.chat(messages, tools.specs, model = model)
            if (reply.content.isNotBlank()) last = reply.content
            if (reply.calls.isEmpty()) return last.ifBlank { "Done." }

            messages += NimClient.Msg("assistant", reply.content, toolCalls = reply.calls)
            for (call in reply.calls) {
                val result = tools.call(call.name, call.args, agent).ifBlank { "No output." }
                messages += NimClient.Msg("tool", result, toolCallId = call.id)
            }
        }
        return wrapUp(model, messages, last.ifBlank { "Stopped after eight tool rounds." })
    }

    /**
     * Tool budget exhausted: ask for prose without tools so the owner gets a real report
     * instead of a dead-end "Stopped after N tool rounds" bubble.
     */
    private suspend fun wrapUp(
        model: String,
        messages: List<NimClient.Msg>,
        fallback: String
    ): String {
        val closing = messages + NimClient.Msg(
            "user",
            "Tool budget reached. Do not call any more tools. Reply to the owner in one short paragraph: what you completed, what is blocked, and the next concrete step."
        )
        val reply = try {
            nim.chat(closing, tools = emptyList(), model = model, maxTokens = 1600)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return fallback
        }
        return reply.content.ifBlank { fallback }
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
            appendLine("If a model or network call fails, report the failure plainly. Never describe failed work as done.")
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
        // An unknown role used to land on the first non-lead bot every time, which is how Scout
        // ended up doing everyone's work. Spread it to whoever has waited longest instead.
        val lastWorked = store.snapshot().tasks.associate { it.assigneeId to it.createdAt }
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                val role = item.optString("role")
                val agent = team.firstOrNull {
                    it.role.equals(role, ignoreCase = true) || it.name.equals(role, ignoreCase = true)
                }
                    ?: team.filter { !it.primary }.minByOrNull { lastWorked[it.id] ?: 0L }
                    ?: team.firstOrNull()
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

        // 2.12 saved the retired deepseek-v4.1 id. Move it to the live id before the loop starts.
        val latest = store.snapshot()
        val migratedSettings = latest.settings.copy(model = Models.migrate(latest.settings.model))
        val migratedAgents = latest.agents.map {
            if (it.model.isNotBlank()) it.copy(model = Models.migrate(it.model)) else it
        }
        if (migratedSettings.model != latest.settings.model || migratedAgents != latest.agents) {
            store.update { it.copy(settings = migratedSettings, agents = migratedAgents) }
        }
        seedBusinessFacts()
    }

    /**
     * A local, no-API onboarding pass. The lead asked for a crew; if the model failed, or chose
     * not to hire, the company still gets its core people and can start working next shift.
     */
    suspend fun ensureCoreTeam() {
        crewMutex.withLock {
            if (store.snapshot().agents.size > 1) return@withLock
            ensureCoreTeamLocked()
        }
    }

    private suspend fun ensureCoreTeamLocked() {
        val current = store.snapshot()
        val lead = current.orchestrator ?: return

        say(
            lead,
            "I'm onboarding the core crew: Scout (research), Closer (sales), Spark (marketing), " +
                "Maker (product), Demo (showcase) and Proof (proofreading).",
            kind = MessageKind.SYSTEM
        )
        val crew = listOf(
            Triple("Research", "Scout", "Finds real local businesses, verifies names, sites and contact details, and records what the company can sell each one."),
            Triple("Sales", "Closer", "Writes short, specific outreach, follows up, handles objections, and records payments with record_sale."),
            Triple("Marketing", "Spark", "Builds offers, campaigns, landing copy and market research that make the company findable."),
            Triple("Product", "Maker", "Turns a sold offer into the finished deliverable: sites, copy, designs, documents and code."),
            Triple("Showcase", "Demo", "Makes customer-ready samples of every offer so buyers can see the quality before they pay."),
            Triple("Proofreader", "Proof", "Removes AI tells, fixes unsupported claims, and polishes every outbound email, product description and sample.")
        )
        crew.forEach { (role, name, charter) ->
            hire(role, name, charter, by = "orchestrator")
        }
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
