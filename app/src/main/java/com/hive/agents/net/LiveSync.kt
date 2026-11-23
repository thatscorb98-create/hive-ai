package com.hive.agents.net

import com.hive.agents.data.HiveState
import com.hive.agents.data.HiveStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pushes a snapshot to the Hive Station relay. Push-only: the phone stays the source of truth,
 * and the dashboard reads the relay instead of waiting for a state file.
 */
object LiveSync {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val MIN_KEY_LENGTH = 16

    private val _status = MutableStateFlow("")
    /** Human-readable result of the latest push, shown on the settings screen. */
    val status: StateFlow<String> = _status.asStateFlow()

    private var scope: CoroutineScope? = null

    fun start(store: HiveStore) {
        if (scope != null) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { created ->
            created.launch {
                store.state.collectLatest { snapshot ->
                    if (!configured(snapshot)) return@collectLatest
                    // Let a burst of state writes settle before uploading.
                    delay(1_500)
                    runCatching { push(snapshot) }
                        .onSuccess {
                            val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                            _status.value = "Live sync pushed at $time"
                        }
                        .onFailure { error ->
                            _status.value = "Live sync failed: ${error.message ?: "upload failed"}"
                        }
                }
            }
        }
    }

    fun configured(state: HiveState): Boolean =
        state.settings.syncUrl.trim().startsWith("https://") &&
            state.settings.syncKey.trim().length >= MIN_KEY_LENGTH

    /** Uploads [state], leaving the relay URL and key out of the stored document. */
    suspend fun push(state: HiveState) {
        val url = state.settings.syncUrl.trim()
        val key = state.settings.syncKey.trim()
        check(configured(state)) { "Live sync needs an https URL and a key of at least 16 characters." }

        val clean = state.copy(settings = state.settings.copy(syncUrl = "", syncKey = ""))
        val body = json.encodeToString(clean)
        check(body.toByteArray().size <= MAX_BYTES) { "Snapshot is larger than 4 MB." }

        Http.putJson(url, body, mapOf("x-hive-key" to key))
    }
}
