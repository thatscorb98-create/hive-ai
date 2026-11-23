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
 * so a crash mid-write cannot leave a half-written state file behind.
 */
class HiveStore(context: Context) {

    private val file = File(context.filesDir, "hive-state.json")
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
        val next = block(_state.value)
        _state.value = next
        write(next)
    }

    private fun load(): HiveState = runCatching {
        if (file.exists()) json.decodeFromString<HiveState>(file.readText()) else HiveState()
    }.getOrElse { HiveState() }

    private fun write(state: HiveState) {
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(state))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
