package com.hive.agents.agent

import com.hive.agents.data.Agent
import com.hive.agents.data.HiveState
import com.hive.agents.data.HiveStore
import com.hive.agents.data.MemoryKind
import com.hive.agents.data.Message
import com.hive.agents.data.MessageKind
import com.hive.agents.data.Mission
import com.hive.agents.data.MissionStatus
import com.hive.agents.data.Task
import com.hive.agents.data.TaskStatus
import com.hive.agents.net.NimClient
import org.json.JSONArray
import java.util.UUID

/**
 * The orchestrator. It reads a mission, hires the roles it needs, splits the work,
 * hands tasks to the right agent and records everything in one shared thread.
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
        val existing = store.snapshot().agents.firstOrNull { it.role.equals(role, ignoreCase = true) }
        if (existing != null) return existing

        val agent = Agent(
            id = UUID.randomUUID().toString(),
            name = name,
            role = role,
            charter = charter,
            hiredBy = by,
            primary = role.equals("Orchestrator", ignoreCase = true)
        )
        store.update { state -> state.copy(agents = state.agents + agent) }
        memory.remember(
            agent.id,
            MemoryKind.WORKLOG,
            "Hired as $role. Charter: $charter",
            source = "hiring"
        )
        return agent
    }

    /** Asks the model which roles this mission needs, then hires the missing ones. */
    suspend fun ensureTeam(mission: Mission): List<Agent> {
        val state = store.snapshot()
        if (state.agents.size >= 2) return state.agents

        val prompt = """
            You are the orchestrator of a small AI agency that runs the business "${state.settings.businessName}".
            Mission: ${mission.title}
            Brief: ${mission.brief}

            List the specialist roles needed. Reply with a JSON array only, each item:
            {"name":"short bot name","role":"Sales","charter":"one sentence"}
            Maximum 4 roles. Always include an Orchestrator first if none exists.
        """.trimIndent()

        val reply = nim.chat(listOf(NimClient.Msg("system", prompt)), maxTokens = 400)
        val roles = parseRoles(reply.content).ifEmpty { defaultRoles() }

        roles.forEach { item ->
            hire(item.first, item.second, item.third)
        }

        val orchestrator = store.snapshot().orchestrator
        if (orchestrator != null && store.snapshot().agents.none { it.primary }) {
            store.update { s ->
                s.copy(agents = s.agents.map { if (it.id == orchestrator.id) it.copy(primary = true) else it })
            }
        }
        return store.snapshot().agents
    }

    suspend fun runMission(missionId: String) {
        val start = store.snapshot()
        val mission = start.missions.firstOrNull { it.id == missionId } ?: return

        setMission(missionId, MissionStatus.RUNNING, "")
        say(start.orchestrator, "Starting \"${mission.title}\".", missionId, kind = MessageKind.SYSTEM)

        val team = ensureTeam(mission)
        val planned = planTasks(mission, team)
        store.update { s -> s.copy(tasks = s.tasks + planned) }

        var failed = false
        for (task in planned) {
            val agent = store.snapshot().agent(task.assigneeId)
                ?: team.firstOrNull { !it.primary }
                ?: team.firstOrNull()
                ?: break

            updateTask(task.id) { it.copy(assigneeId = agent.id, status = TaskStatus.RUNNING) }
            say(agent, "On it: ${task.title}", missionId, kind = MessageKind.SYSTEM)

            runCatching { dispatch(agent, task, mission) }
                .onSuccess { result ->
                    updateTask(task.id) { it.copy(status = TaskStatus.DONE, output = result) }
                    say(agent, result, missionId)
                }
                .onFailure { error ->
                    failed = true
                    val message = error.message ?: "unknown error"
                    updateTask(task.id) { it.copy(status = TaskStatus.FAILED, output = message) }
                    memory.lesson(
                        agent.id,
                        "Task \"${task.title}\" failed: $message",
                        "Read the tool output before retrying, and never repeat a call that already failed the same way."
                    )
                    suggestions.fromFailure(agent, task.title, message)
                    say(agent, "Blocked: $message", missionId, kind = MessageKind.SYSTEM)
                }
        }

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

    private suspend fun planTasks(mission: Mission, team: List<Agent>): List<Task> {
        val roster = team.joinToString("\n") { "- ${it.name} :: ${it.role}" }
        val prompt = """
            Mission: ${mission.title}
            Brief: ${mission.brief}
            Team:
            $roster

            Break the mission into 2-4 concrete tasks. Reply with a JSON array only, each item:
            {"title":"short task","detail":"what done looks like","role":"role from the team"}
        """.trimIndent()

        val reply = nim.chat(listOf(NimClient.Msg("system", prompt)), maxTokens = 500)
        val parsed = parseTasks(reply.content, team)
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

    private suspend fun dispatch(agent: Agent, task: Task, mission: Mission): String {
        val messages = mutableListOf(
            NimClient.Msg("system", systemPrompt(agent)),
            NimClient.Msg(
                "user",
                buildString {
                    appendLine("Mission: ${mission.title}")
                    appendLine("Brief: ${mission.brief}")
                    appendLine("Your task: ${task.title}")
                    if (task.detail.isNotBlank()) appendLine(task.detail)
                    val guard = memory.guardrails(agent.id, "${mission.title} ${task.title}")
                    if (guard.isNotBlank()) {
                        appendLine()
                        appendLine(guard)
                    }
                    appendLine()
                    appendLine("Use tools when they help. Finish with a short report of what you did.")
                }
            )
        )

        var last = ""
        repeat(4) {
            val reply = nim.chat(messages, tools.specs)
            if (reply.content.isNotBlank()) last = reply.content
            if (reply.calls.isEmpty()) return last.ifBlank { "Done." }

            messages += NimClient.Msg("assistant", reply.content.ifBlank { "Calling tools." })
            for (call in reply.calls) {
                val result = tools.call(call.name, call.args, agent)
                messages += NimClient.Msg("user", "Result of ${call.name}: $result")
            }
        }
        return last.ifBlank { "Stopped after four tool rounds." }
    }

    private fun systemPrompt(agent: Agent): String {
        val state = store.snapshot()
        val facts = memory.sharedFacts().joinToString("\n") { "- ${it.text}" }
        return buildString {
            appendLine("You are ${agent.name}, the ${agent.role} at ${state.settings.businessName}.")
            appendLine("Your charter: ${agent.charter}")
            if (facts.isNotBlank()) {
                appendLine("Business facts:")
                appendLine(facts)
            }
            appendLine("You share a team chat with other bots. Be concrete, use tools, never invent results.")
            appendLine("Anything that costs money or leaves the building needs human approval.")
        }
    }

    private fun parseRoles(raw: String): List<Triple<String, String, String>> {
        val json = extractJsonArray(raw) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                val role = item.optString("role").ifBlank { "Specialist" }
                val name = item.optString("name").ifBlank { role }
                val charter = item.optString("charter")
                Triple(role, name, charter)
            }
        }.getOrDefault(emptyList())
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

    private fun defaultRoles(): List<Triple<String, String, String>> = listOf(
        Triple("Orchestrator", "Chief", "Runs the team, splits work, reports to the human."),
        Triple("Sales", "Scout", "Finds and qualifies leads, drafts outreach."),
        Triple("Studio", "Maker", "Produces websites, graphics, copy and digital goods.")
    )

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

    /** Called once on first launch so the app always has an orchestrator. */
    fun bootstrap() {
        val state: HiveState = store.snapshot()
        if (state.agents.isNotEmpty()) return
        store.update { current ->
            current.copy(
                agents = listOf(
                    Agent(
                        id = UUID.randomUUID().toString(),
                        name = "Chief",
                        role = "Orchestrator",
                        charter = "Runs the team, splits work, reports to the human.",
                        primary = true,
                        hiredBy = "human"
                    )
                )
            )
        }
    }
}
