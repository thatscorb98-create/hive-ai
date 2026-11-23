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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val suggestions: SuggestionBox,
    private val shifts: ShiftCoordinator
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
        tools.beginTurn(agent.id)
        var last = ""
        repeat(8) {
            val reply = nim.chat(messages, tools.specsFor(agent), model = model)
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
        try {
            val healthy = executeMission(mission)
            finishMission(mission, healthy)
        } catch (cancel: CancellationException) {
            // A stopped shift is resumable, not failed. Leaving the mission RUNNING would make
            // the next shift plan the same work again without knowing what was interrupted.
            setMission(mission.id, MissionStatus.PLANNED, "Paused: the app stopped the shift.")
            store.update { state ->
                state.copy(tasks = state.tasks.filterNot {
                    it.missionId == mission.id && it.status == TaskStatus.QUEUED
                })
            }
            throw cancel
        }
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

        val healthy = shifts.run { executeMission(mission) }
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
        currentCoroutineContext().ensureActive()
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
                currentCoroutineContext().ensureActive()
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
        val bots = planned.map { it.assigneeId }.distinct().size
        say(lead, "Shift plan, $bots bots working:\n$lines", mission.id, kind = MessageKind.SYSTEM)
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
        val rendered = untrustedBody(text.trim())
        store.update { state ->
            state.copy(
                messages = state.messages + message.copy(
                    body = if (fromUser) rendered else message.body
                )
            )
        }
    }

    /** Human-typed text is untrusted content: mark it so the transcript shows the boundary. */
    private fun untrustedBody(text: String): String = text

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

        return try {
            val result = dispatch(agent, task, mission, handoff)
            updateTask(task.id) { it.copy(status = TaskStatus.DONE, output = result) }
            say(
                agent,
                result,
                mission.id,
                toAgentId = toLead,
                kind = if (toLead.isNotBlank()) MessageKind.HANDOFF else MessageKind.CHAT
            )
            true
        } catch (cancel: CancellationException) {
            // runCatching used to swallow cancellation here, so stopping Autopilot or the
            // WorkManager worker still spent calls. Put the task back in the queue and let the
            // cancellation propagate.
            updateTask(task.id) { it.copy(status = TaskStatus.QUEUED, output = "") }
            throw cancel
        } catch (error: Exception) {
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
    }

    private suspend fun dispatch(
        agent: Agent,
        task: Mission,
        mission: Mission,
        handoff: List<String> = emptyList()
    ): String {
        return dispatch(agent, Task(id = task.id, missionId = mission.id, title = task.title, detail = task.brief, assigneeId = agent.id), mission, handoff)
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
        tools.beginTurn(agent.id)
        repeat(8) {
            val reply = nim.chat(messages, tools.specsFor(agent), model = model)
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
