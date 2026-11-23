package com.hive.agents.net

import com.hive.agents.agent.ToolSpec
import com.hive.agents.data.KeyVault
import com.hive.agents.data.Models
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** A model call that actually failed. It must never be mistaken for a bot's answer. */
class NimException(message: String) : Exception(message)

/**
 * Thin client for the NVIDIA-hosted OpenAI-compatible endpoint.
 *
 * NVIDIA retires model ids without notice (the old DeepSeek v4.1 id now returns HTTP 410 Gone).
 * Before the first call we read /v1/models, move any saved id to its current name, and pick a
 * live model from the same family when the requested one is missing. A second 404/410 retries
 * once on another live model. If the call still fails, [chat] throws [NimException] so the task
 * is marked failed instead of "Mission complete".
 */
class NimClient(private val vault: KeyVault) {

    data class Msg(val role: String, val content: String)

    data class Call(val id: String, val name: String, val args: String)

    data class Reply(val content: String, val calls: List<Call>) {
        val isEmpty: Boolean get() = content.isBlank() && calls.isEmpty()
    }

    @Volatile private var catalog: List<String> = emptyList()
    @Volatile private var catalogAt: Long = 0L

    suspend fun chat(
        messages: List<Msg>,
        tools: List<ToolSpec> = emptyList(),
        model: String = Models.DEFAULT,
        maxTokens: Int = 1200,
        temperature: Double = 0.4
    ): Reply {
        val key = vault.load().nimApiKey
        if (key.isBlank()) throw NimException(MISSING_KEY)

        val resolved = resolveModel(model, key)
        val body = requestBody(resolved, messages, tools, maxTokens, temperature)

        return try {
            parse(post(body, key))
        } catch (error: HttpException) {
            if (error.code == 404 || error.code == 410) {
                // The catalog moved under us. Forget it, pick another live model, retry once.
                catalog = emptyList()
                catalogAt = 0L
                val fallback = fallbackModel(resolved, key)
                if (fallback != resolved) {
                    body.put("model", fallback)
                    try {
                        parse(post(body, key))
                    } catch (retry: HttpException) {
                        throw describe(retry)
                    }
                } else {
                    throw describe(error)
                }
            } else {
                throw describe(error)
            }
        } catch (error: NimException) {
            throw error
        } catch (error: Exception) {
            throw NimException("model call failed: ${error.message?.take(220) ?: "unknown error"}")
        }
    }

    private fun requestBody(
        model: String,
        messages: List<Msg>,
        tools: List<ToolSpec>,
        maxTokens: Int,
        temperature: Double
    ): JSONObject {
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().apply {
                messages.forEach { m -> put(JSONObject().put("role", m.role).put("content", m.content)) }
            })
            .put("max_tokens", maxTokens)
            .put("temperature", temperature)

        if (tools.isNotEmpty()) {
            body.put("tools", JSONArray().apply { tools.forEach { put(it.toWire()) } })
            body.put("tool_choice", "auto")
        }
        return body
    }

    private suspend fun post(body: JSONObject, key: String): String = Http.postJson(
        "$BASE/chat/completions",
        body.toString(),
        mapOf("Authorization" to "Bearer $key")
    )

    private suspend fun resolveModel(requested: String, key: String): String {
        val wanted = Models.migrate(requested)
        val live = availableModels(key)
        if (live.isEmpty() || wanted in live) return wanted

        val family = wanted.substringBefore('/')
        val sameFamily = live.filter { it.substringBefore('/') == family && isChatModel(it) }
        val knownGood = listOf(Models.SUPER, Models.LIGHTNING, Models.DEEPSEEK).filter { it in live }
        return (sameFamily + knownGood).firstOrNull()
            ?: live.firstOrNull { isChatModel(it) }
            ?: wanted
    }

    private suspend fun fallbackModel(failed: String, key: String): String {
        val live = availableModels(key)
        val pool = live.filter { it != failed && isChatModel(it) }
        val knownGood = listOf(Models.SUPER, Models.LIGHTNING, Models.DEEPSEEK)
            .filter { it != failed && (live.isEmpty() || it in pool) }
        return (knownGood + pool).firstOrNull() ?: failed
    }

    private suspend fun availableModels(key: String): List<String> {
        val now = System.currentTimeMillis()
        if (catalog.isNotEmpty() && now - catalogAt < CATALOG_TTL_MS) return catalog

        val raw = try {
            Http.get("$BASE/models", mapOf("Authorization" to "Bearer $key"))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        } ?: return catalog

        val ids = runCatching {
            val data = JSONObject(raw).optJSONArray("data") ?: return@runCatching emptyList<String>()
            (0 until data.length()).mapNotNull { index ->
                data.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())

        if (ids.isNotEmpty()) {
            catalog = ids
            catalogAt = now
        }
        return catalog
    }

    private fun isChatModel(id: String): Boolean {
        val lower = id.lowercase()
        return listOf("embed", "rerank", "reward", "guard", "pii", "safety", "parse")
            .none { lower.contains(it) }
    }

    private fun describe(error: HttpException): NimException {
        val detail = runCatching {
            val json = JSONObject(error.body)
            json.optString("detail")
                .ifBlank { json.optString("title") }
                .ifBlank { json.optString("message") }
        }.getOrNull().orEmpty().ifBlank { error.body }
        return NimException("model call failed (HTTP ${error.code}): ${detail.take(220)}")
    }

    private fun parse(raw: String): Reply {
        val root = runCatching { JSONObject(raw) }.getOrElse {
            throw NimException("could not read the model response: ${it.message?.take(120)}")
        }
        val message = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?: throw NimException("the model returned no message")

        val content = message.opt("content")
            ?.takeIf { it != JSONObject.NULL }
            ?.toString()
            .orEmpty()
            .trim()

        val calls = mutableListOf<Call>()
        val arr = message.optJSONArray("tool_calls")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val fn = item.optJSONObject("function") ?: continue
                calls += Call(
                    id = item.optString("id", "call_$i"),
                    name = fn.optString("name"),
                    args = fn.optString("arguments", "{}")
                )
            }
        }

        if (content.isBlank() && calls.isEmpty()) {
            throw NimException("the model returned an empty response")
        }
        return Reply(content, calls)
    }

    companion object {
        const val BASE = "https://integrate.api.nvidia.com/v1"
        const val MISSING_KEY = "No NVIDIA API key yet. Add one in Settings."
        private const val CATALOG_TTL_MS = 10 * 60_000L
    }
}
