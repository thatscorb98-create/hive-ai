package com.hive.agents.net

import com.hive.agents.agent.ToolSpec
import com.hive.agents.data.KeyVault
import com.hive.agents.data.Models
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** A model call that actually failed. It must never be mistaken for a bot's answer. */
class NimException(
    message: String,
    /** True when the endpoint returned HTTP 200 but no answer and no tool call. */
    val emptyResponse: Boolean = false
) : Exception(message)

/**
 * Thin client for the NVIDIA-hosted OpenAI-compatible endpoint.
 *
 * NVIDIA retires model ids without notice (the old DeepSeek v4.1 id now returns HTTP 410 Gone).
 * Before the first call we read /v1/models, move any saved id to its current name, and pick a
 * live model from the same family when the requested one is missing. A 404/410 retries once on
 * another live model. If the call still fails, [chat] throws [NimException] so the task is
 * marked failed instead of "Mission complete".
 *
 * These are reasoning models. Hidden reasoning tokens count against `max_tokens`, so an
 * undersized budget returns HTTP 200 with `content: null` and no tool calls. We keep a generous
 * default, ask the server to force non-empty content when tools are offered, and retry an empty
 * answer once with twice the budget before falling back to another live model.
 */
class NimClient(private val vault: KeyVault) {

    data class Msg(
        val role: String,
        val content: String,
        /**
         * For an assistant turn that called tools: the calls exactly as the model returned them.
         * The next request must echo these back, or the model cannot connect results to calls.
         */
        val toolCalls: List<Call> = emptyList(),
        /** For role="tool": the id of the call this result belongs to. */
        val toolCallId: String = ""
    )

    data class Call(val id: String, val name: String, val args: String) {
        fun toWire(): JSONObject = JSONObject()
            .put("id", id)
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("arguments", args.ifBlank { "{}" })
            )
    }

    data class Reply(val content: String, val calls: List<Call>) {
        val isEmpty: Boolean get() = content.isBlank() && calls.isEmpty()
    }

    @Volatile private var catalog: List<String> = emptyList()
    @Volatile private var catalogAt: Long = 0L

    suspend fun chat(
        messages: List<Msg>,
        tools: List<ToolSpec> = emptyList(),
        model: String = Models.DEFAULT,
        maxTokens: Int = 8192,
        temperature: Double = 0.4
    ): Reply {
        val key = vault.load().nimApiKey
        if (key.isBlank()) throw NimException(MISSING_KEY)

        val resolved = resolveModel(model, key)
        val body = requestBody(resolved, messages, tools, maxTokens, temperature)

        return try {
            attemptWithEmptyRetry(body, key, resolved, maxTokens)
        } catch (error: HttpException) {
            if (error.code == 404 || error.code == 410) {
                // The catalog moved under us. Forget it, pick another live model, retry once.
                catalog = emptyList()
                catalogAt = 0L
                val fallback = fallbackModel(resolved, key)
                if (fallback != resolved) {
                    body.put("model", fallback)
                    addTemplate(body, fallback)
                    try {
                        attemptWithEmptyRetry(body, key, fallback, maxTokens)
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
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw NimException("model call failed: ${error.message?.take(220) ?: "unknown error"}")
        }
    }

    /**
     * Empty content is not a bot answer. Reasoning can consume the whole completion, so give the
     * same model one bigger attempt, then try a different live model before failing the task.
     */
    private suspend fun attemptWithEmptyRetry(
        body: JSONObject,
        key: String,
        model: String,
        maxTokens: Int
    ): Reply {
        var lastEmpty: NimException? = null
        try {
            return parse(post(body, key))
        } catch (error: NimException) {
            if (!error.emptyResponse) throw error
            lastEmpty = error
        }

        body.put("max_tokens", maxOf(maxTokens * 2, 8192))
        try {
            return parse(post(body, key))
        } catch (error: NimException) {
            if (!error.emptyResponse) throw error
            lastEmpty = error
        } catch (error: HttpException) {
            throw error
        }

        val fallback = fallbackModel(model, key)
        if (fallback == model) {
            val why = lastEmpty?.message ?: "the model returned an empty response"
            throw NimException("$why on $model", emptyResponse = true)
        }

        body.put("model", fallback)
        addTemplate(body, fallback)
        try {
            return parse(post(body, key))
        } catch (error: HttpException) {
            throw describe(error)
        } catch (error: NimException) {
            if (error.emptyResponse) {
                throw NimException("${error.message} on $fallback", emptyResponse = true)
            }
            throw error
        }
    }

    private fun requestBody(
        model: String,
        messages: List<Msg>,
        tools: List<ToolSpec>,
        maxTokens: Int,
        temperature: Double
    ): JSONObject {
        val wire = JSONArray()
        messages.forEach { m ->
            val item = JSONObject().put("role", m.role)
            if (m.toolCallId.isNotBlank()) item.put("tool_call_id", m.toolCallId)
            if (m.toolCalls.isNotEmpty()) {
                // An assistant tool call has no prose of its own. Sending "Calling tools."
                // instead of the real calls is what made the model repeat itself.
                item.put("content", if (m.content.isBlank()) JSONObject.NULL else m.content)
                item.put(
                    "tool_calls",
                    JSONArray().apply { m.toolCalls.forEach { put(it.toWire()) } }
                )
            } else {
                item.put("content", m.content)
            }
            wire.put(item)
        }

        val body = JSONObject()
            .put("model", model)
            .put("messages", wire)
            .put("max_tokens", maxTokens)
            .put("temperature", temperature)

        if (tools.isNotEmpty()) {
            body.put("tools", JSONArray().apply { tools.forEach { put(it.toWire()) } })
            body.put("tool_choice", "auto")
        }
        addTemplate(body, model)
        return body
    }

    /**
     * Nemotron's chat template can return no visible content when the model spends the whole
     * completion on hidden reasoning. `force_nonempty_content` makes the parser return the
     * reasoning text instead of an empty message. Other families do not share that template,
     * so we only send it to Nemotron, and never as a top-level parameter.
     */
    private fun addTemplate(body: JSONObject, model: String) {
        body.remove("chat_template_kwargs")
        if (body.has("tools") && model.contains("nemotron", ignoreCase = true)) {
            body.put(
                "chat_template_kwargs",
                JSONObject().put("force_nonempty_content", true)
            )
        }
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
        val choice = root.optJSONArray("choices")?.optJSONObject(0)
            ?: throw NimException("the model returned no choice")
        val message = choice.optJSONObject("message")
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
                val name = fn.optString("name")
                if (name.isBlank()) continue
                calls += Call(
                    id = item.optString("id").takeIf { it.isNotBlank() } ?: "call_$i",
                    name = name,
                    args = fn.optString("arguments", "{}")
                )
            }
        }

        if (content.isBlank() && calls.isEmpty()) {
            val finish = choice.optString("finish_reason")
            val reasoning = message.optString("reasoning_content").isNotBlank() ||
                message.optString("reasoning").isNotBlank()
            val why = when {
                finish == "length" ->
                    "finish_reason=length; the token budget ran out before an answer"
                reasoning -> "only reasoning came back, with no answer"
                finish.isNotBlank() -> "finish_reason=$finish"
                else -> "no content and no tool calls"
            }
            throw NimException("the model returned an empty response ($why)", emptyResponse = true)
        }
        return Reply(content, calls)
    }

    companion object {
        const val BASE = "https://integrate.api.nvidia.com/v1"
        const val MISSING_KEY = "No NVIDIA API key yet. Add one in Settings."
        private const val CATALOG_TTL_MS = 10 * 60_000L
    }
}
