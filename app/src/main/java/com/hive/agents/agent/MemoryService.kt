package com.hive.agents.agent

import com.hive.agents.data.HiveStore
import com.hive.agents.data.MemoryEntry
import com.hive.agents.data.MemoryKind
import java.util.UUID

/**
 * Per-agent memory, including the mistake ledger.
 *
 * A LESSON entry is a mistake: [MemoryEntry.context] holds what went wrong and
 * [MemoryEntry.text] holds what to do instead. Lessons are recalled before similar work.
 */
class MemoryService(private val store: HiveStore) {

    fun remember(
        agentId: String,
        kind: MemoryKind,
        text: String,
        context: String = "",
        source: String = "agent"
    ) {
        val clean = text.trim()
        if (clean.isBlank()) return
        store.update { state ->
            val duplicate = state.memory.any {
                it.agentId == agentId && it.kind == kind && it.text.equals(clean, ignoreCase = true)
            }
            if (duplicate) {
                state
            } else {
                state.copy(
                    memory = state.memory + MemoryEntry(
                        id = UUID.randomUUID().toString(),
                        agentId = agentId,
                        kind = kind,
                        text = clean.take(600),
                        context = context.take(600),
                        source = source
                    )
                )
            }
        }
    }

    fun lesson(agentId: String, mistake: String, instead: String) {
        remember(agentId, MemoryKind.LESSON, instead, context = mistake, source = "review")
    }

    /** Keyword overlap plus recency. Cheap on purpose: this runs on every step. */
    fun recall(agentId: String, query: String, limit: Int = 6): List<MemoryEntry> {
        val tokens = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 3 }.toSet()
        val candidates = store.snapshot().memory.filter { it.agentId == agentId || it.agentId == "*" }

        return candidates
            .map { entry ->
                val haystack = "${entry.text} ${entry.context}".lowercase()
                val overlap = tokens.count { haystack.contains(it) }
                val weight = when (entry.kind) {
                    MemoryKind.RULE -> 3
                    MemoryKind.LESSON -> 4
                    MemoryKind.PREFERENCE -> 2
                    else -> 1
                }
                entry to (overlap * 10 + weight)
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    fun forget(id: String) {
        store.update { state -> state.copy(memory = state.memory.filterNot { it.id == id }) }
    }

    fun sharedFacts(): List<MemoryEntry> =
        store.snapshot().memory.filter { it.agentId == "*" && it.kind != MemoryKind.WORKLOG }

    /** Runs before a retry so the same mistake is not repeated. */
    fun guardrails(agentId: String, taskTitle: String): String {
        val entries = recall(agentId, taskTitle, limit = 5)
        if (entries.isEmpty()) return ""
        return buildString {
            appendLine("What you already know (do not repeat past mistakes):")
            entries.forEach { entry ->
                appendLine("- [${entry.kind.name.lowercase()}] ${entry.text}")
                if (entry.context.isNotBlank()) appendLine("  (because: ${entry.context})")
            }
        }
    }
}
