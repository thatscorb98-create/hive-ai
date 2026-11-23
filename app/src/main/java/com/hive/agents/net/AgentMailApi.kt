package com.hive.agents.net

import com.hive.agents.data.InboxMail
import com.hive.agents.data.KeyVault
import com.hive.agents.data.SentMail
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Agent email through AgentMail.
 *
 * Contract: every agent has its own address, so one API key and one inbox per agent.
 * Endpoints follow the v0 REST shape (`/inboxes/{inbox}/messages`); if AgentMail changes
 * the paths, only this file needs to change.
 */
class AgentMailApi(private val vault: KeyVault) {

    suspend fun send(
        inbox: String,
        from: String,
        to: String,
        subject: String,
        text: String
    ): SentMail {
        val keys = vault.load()
        require(keys.agentMailApiKey.isNotBlank()) { "No AgentMail API key. Add one in Settings." }
        val target = inbox.ifBlank { keys.agentMailInbox }
        require(target.isNotBlank()) { "No agent inbox configured." }

        val body = JSONObject()
            .put("to", JSONArray().put(to))
            .put("subject", subject)
            .put("text", text)

        Http.postJson(
            "$BASE/inboxes/$target/messages/send",
            body.toString(),
            mapOf("Authorization" to "Bearer ${keys.agentMailApiKey}")
        )

        return SentMail(
            id = UUID.randomUUID().toString(),
            agentId = "",
            from = from.ifBlank { target },
            to = to,
            subject = subject,
            body = text
        )
    }

    /** Reads the agent's inbox. Returns an empty list rather than throwing on a shape change. */
    suspend fun inbox(inbox: String): List<InboxMail> {
        val keys = vault.load()
        if (keys.agentMailApiKey.isBlank()) return emptyList()
        val target = inbox.ifBlank { keys.agentMailInbox }
        if (target.isBlank()) return emptyList()

        val raw = runCatching {
            Http.get(
                "$BASE/inboxes/$target/messages?limit=50",
                mapOf("Authorization" to "Bearer ${keys.agentMailApiKey}")
            )
        }.getOrNull() ?: return emptyList()

        return runCatching {
            val arr = JSONObject(raw).optJSONArray("messages") ?: JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val m = arr.optJSONObject(i) ?: return@mapNotNull null
                InboxMail(
                    id = m.optString("id", UUID.randomUUID().toString()),
                    from = m.optString("from", m.optString("sender", "")),
                    subject = m.optString("subject", "(no subject)"),
                    preview = m.optString("preview", m.optString("text", "")).take(160),
                    body = m.optString("text", m.optString("body", "")),
                    receivedAt = m.optLong("created_at", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val BASE = "https://api.agentmail.to/v0"
    }
}
