package com.hive.agents.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Single source of truth for the app.
 *
 * Written atomically: the new state goes to a temp file and is renamed over the old one,
 * so a crash mid-write cannot leave a half-written state file behind. A second copy is
 * kept in hive-state.bak and used if the main file is ever unreadable.
 */
class HiveStore(context: Context) {

    private val file = File(context.filesDir, "hive-state.json")
    private val backup = File(context.filesDir, "hive-state.bak")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<HiveState> = _state.asStateFlow()

    fun snapshot(): HiveState = _state.value

    @Synchronized
    fun update(block: (HiveState) -> HiveState) {
        val next = trim(block(_state.value))
        _state.value = next
        write(next)
    }

    /**
     * A working shift writes about twenty messages, and the whole state is rewritten on every
     * change. Without a cap the file grows until every write is slow, which makes a busy company
     * feel broken. Old chat lines fall off; the pipeline, products and paid work never do.
     */
    private fun trim(state: HiveState): HiveState {
        val messages = state.messages.takeLast(MAX_MESSAGES)
        val tasks = state.tasks.takeLast(MAX_TASKS)
        val memory = state.memory.takeLast(MAX_MEMORY)
        val inbox = state.inbox.take(MAX_INBOX)
        val changed = messages.size != state.messages.size ||
            tasks.size != state.tasks.size ||
            memory.size != state.memory.size ||
            inbox.size != state.inbox.size
        return if (changed) {
            state.copy(messages = messages, tasks = tasks, memory = memory, inbox = inbox)
        } else {
            state
        }
    }

    private fun load(): HiveState {
        read(file)?.let { return it }
        read(backup)?.let { restored ->
            runCatching { file.writeText(backup.readText()) }
            return restored
        }
        return HiveState()
    }

    private fun read(source: File): HiveState? = runCatching {
        if (source.exists()) json.decodeFromString<HiveState>(source.readText()) else null
    }.getOrNull()

    private companion object {
        const val MAX_MESSAGES = 600
        const val MAX_TASKS = 400
        const val MAX_MEMORY = 800
        const val MAX_INBOX = 200
    }

    private fun write(state: HiveState) {
        runCatching {
            val payload = json.encodeToString(state)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(payload)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            backup.writeText(payload)
        }
    }
}
