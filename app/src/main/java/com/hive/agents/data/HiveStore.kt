package com.hive.agents.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for the app.
 *
 * Writes happen on one background thread. The previous version encoded and wrote the whole
 * pretty-printed state inside every `update`, and Compose event handlers call `update` on the
 * main thread; as the state grew that became an ANR risk. The in-memory state still changes
 * immediately, so the UI is not waiting on disk.
 *
 * The file is written atomically (temp file + rename) and mirrored to `hive-state.bak`, so a
 * crash mid-write cannot leave a half-written state file behind.
 */
class HiveStore internal constructor(private val dir: File) {

    constructor(context: Context) : this(context.filesDir)

    private val file = File(dir, "hive-state.json")
    private val backup = File(dir, "hive-state.bak")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hive-state-writer").apply { isDaemon = true }
    }

    private val _error = MutableStateFlow<String?>(null)

    /** A persistence failure the owner must see. Null means the last write succeeded. */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<HiveState> = _state.asStateFlow()

    fun snapshot(): HiveState = _state.value

    @Synchronized
    fun update(block: (HiveState) -> HiveState) {
        val next = trim(block(_state.value))
        _state.value = next
        writer.execute { write(next) }
    }

    /** Waits for queued writes; used by tests and by a deliberate shutdown. */
    fun flush() {
        runCatching { writer.submit {}.get(10, TimeUnit.SECONDS) }
    }

    /** Stops the writer. Only tests and process teardown should call this. */
    fun close() {
        writer.shutdown()
    }

    /**
     * A working shift writes about twenty messages, and the whole state is rewritten on every
     * change. Without a cap the file grows until every write is slow. Old chat lines, finished
     * missions, sent mail and diagnostics fall off; pipeline, products and paid work never do.
     */
    private fun trim(state: HiveState): HiveState {
        val messages = state.messages.takeLast(MAX_MESSAGES)
        val tasks = state.tasks.takeLast(MAX_TASKS)
        val memory = state.memory.takeLast(MAX_MEMORY)
        val inbox = state.inbox.take(MAX_INBOX)
        val sentMail = state.sentMail.takeLast(MAX_SENT_MAIL)
        val suggestions = state.suggestions.takeLast(MAX_SUGGESTIONS)

        // Never drop a planned/running mission: the next shift resumes it.
        val active = state.missions.filter {
            it.status == MissionStatus.PLANNED || it.status == MissionStatus.RUNNING
        }
        val finished = state.missions.filterNot {
            it.status == MissionStatus.PLANNED || it.status == MissionStatus.RUNNING
        }
        val roomForFinished = (MAX_MISSIONS - active.size).coerceAtLeast(0)
        val missions = (finished.takeLast(roomForFinished) + active).sortedBy { it.createdAt }

        val changed = messages.size != state.messages.size ||
            tasks.size != state.tasks.size ||
            memory.size != state.memory.size ||
            inbox.size != state.inbox.size ||
            sentMail.size != state.sentMail.size ||
            suggestions.size != state.suggestions.size ||
            missions.size != state.missions.size
        return if (changed) {
            state.copy(
                messages = messages,
                tasks = tasks,
                memory = memory,
                inbox = inbox,
                sentMail = sentMail,
                suggestions = suggestions,
                missions = missions
            )
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

    private fun write(state: HiveState) {
        try {
            val payload = json.encodeToString(state)
            val tmp = File(dir, "${file.name}.tmp")
            tmp.writeText(payload)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            backup.writeText(payload)
            _error.value = null
        } catch (error: Exception) {
            _error.value = "Could not save the company state: ${error.message ?: "unknown error"}"
        }
    }

    private companion object {
        const val MAX_MESSAGES = 600
        const val MAX_TASKS = 400
        const val MAX_MEMORY = 800
        const val MAX_INBOX = 200
        const val MAX_MISSIONS = 200
        const val MAX_SENT_MAIL = 300
        const val MAX_SUGGESTIONS = 100
    }
}
