package com.hive.agents.net

import com.hive.agents.agent.ToolSpec
import com.hive.agents.data.KeyVault
import com.hive.agents.data.Models
import org.json.JSONArray
import org.json.JSONObject

/** Thin client for the NVIDIA-hosted OpenAI-compatible endpoint. */
class NimClient(private val vault: KeyVault) {

    data class Msg(val role: String, val content: String)

    data class Call(val id: String, val name: String, val args: String)

    data class Reply(val content: String, val calls: List<Call>) {
        val isEmpty: Boolean get() = content.isBlank() && calls.isEmpty()
    }

    suspend fun chat(
        messages: List<Msg>,
        tools: List<ToolSpec> = emptyList(),
        model: String = Models.DEFAULT,
        maxTokens: Int = 1200,
        temperature: Double = 0.4
    ): Reply {
        val key = vault.load().nimApiKey
        if (key.isBlank()) return Reply(MISSING_KEY, emptyList())

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

        val raw = runCatching {
            Http.postJson(
                "$BASE/chat/completions",
                body.toString(),
                mapOf("Authorization" to "Bearer $key")
            )
        }.getOrElse { return Reply("model call failed: ${it.message}", emptyList()) }

        return parse(raw)
    }

    private fun parse(raw: String): Reply = runCatching {
        val root = JSONObject(raw)
        val message = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?: return Reply("empty model response", emptyList())

        val content = message.opt("content")
            ?.takeIf { it != JSONObject.NULL }
            ?.toString()
            .orEmpty()

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
        Reply(content.trim(), calls)
    }.getOrElse { Reply("could not parse model response: ${it.message}", emptyList()) }

    companion object {
        const val BASE = "https://integrate.api.nvidia.com/v1"
        const val MISSING_KEY = "No NVIDIA API key yet. Add one in Settings."
    }
}
