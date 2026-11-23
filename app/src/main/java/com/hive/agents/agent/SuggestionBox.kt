package com.hive.agents.agent

import com.hive.agents.data.Agent
import com.hive.agents.data.HiveStore
import com.hive.agents.data.Suggestion
import java.util.UUID

/**
 * Turns friction the agents notice into a prompt the human can paste into any coding agent,
 * pointing at this repo.
 */
class SuggestionBox(private val store: HiveStore) {

    fun add(
        title: String,
        problem: String,
        evidence: String = "",
        proposedChange: String = "",
        files: String = "",
        acceptance: String = ""
    ): Suggestion {
        val suggestion = Suggestion(
            id = UUID.randomUUID().toString(),
            title = title,
            problem = problem,
            evidence = evidence,
            proposedChange = proposedChange,
            files = files,
            acceptance = acceptance
        )
        store.update { state -> state.copy(suggestions = state.suggestions + suggestion) }
        return suggestion
    }

    fun fromFailure(agent: Agent, taskTitle: String, error: String): Suggestion = add(
        title = "Fix: $taskTitle keeps failing",
        problem = "Agent ${agent.name} (${agent.role}) could not finish \"$taskTitle\": $error",
        evidence = error.take(500),
        proposedChange = "Find the failing branch, make the failure explicit, and surface it in the mission timeline.",
        files = "app/src/main/java/com/hive/agents/agent/TeamEngine.kt",
        acceptance = "Re-run the mission; the task ends DONE without a retry of the same failure."
    )

    fun markCopied(id: String) {
        store.update { state ->
            state.copy(suggestions = state.suggestions.map { if (it.id == id) it.copy(copied = true) else it })
        }
    }
}
