package com.hive.agents.net

import com.hive.agents.data.InboxMail
import com.hive.agents.data.KeyVault
import com.hive.agents.data.SentMail
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * Agent email through AgentMail.
 *
 * The v0 list endpoint returns `message_id` and `timestamp` (the older parser read `id` and
 * `created_at`, invented a random id and a "now" timestamp for every row, and duplicated the
 * whole inbox on every refresh). HTTP failures now throw so the screen can say what happened;
 * a malformed body still returns an empty list rather than crashing.
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

    /** Reads the agent's inbox. Throws on an HTTP/network failure so the UI can show it. */
    suspend fun inbox(inbox: String): List<InboxMail> {
        val keys = vault.load()
        if (keys.agentMailApiKey.isBlank()) return emptyList()
        val target = inbox.ifBlank { keys.agentMailInbox }
        if (target.isBlank()) return emptyList()

        val raw = Http.get(
            "$BASE/inboxes/$target/messages?limit=50",
            mapOf("Authorization" to "Bearer ${keys.agentMailApiKey}")
        )
        return parseMessages(raw)
    }

    companion object {
        const val BASE = "https://api.agentmail.to/v0"

        /** Official v0 fields first; older shapes are kept as fallbacks. */
        internal fun parseMessages(raw: String): List<InboxMail> = runCatching {
            // A top-level array makes JSONObject(raw) throw, which used to skip the legacy
            // fallback entirely and report an empty inbox. Try the envelope first, then the array.
            val root = runCatching { JSONObject(raw) }.getOrNull()
            val arr = root?.optJSONArray("messages") ?: JSONArray(raw)
            (0 until arr.length()).mapNotNull { index ->
                val item = arr.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("message_id").ifBlank {
                    item.optString("id")
                }.ifBlank {
                    UUID.randomUUID().toString()
                }
                InboxMail(
                    id = id,
                    from = item.optString("from", item.optString("sender", "")),
                    subject = item.optString("subject", "(no subject)"),
                    preview = item.optString("preview", item.optString("text", "")).take(160),
                    body = item.optString("text", item.optString("body", "")),
                    receivedAt = timestamp(item)
                )
            }
        }.getOrDefault(emptyList())

        private fun timestamp(item: JSONObject): Long {
            val raw = item.opt("timestamp") ?: item.opt("created_at") ?: return System.currentTimeMillis()
            if (raw is Number) return raw.toLong()
            return runCatching { Instant.parse(raw.toString()).toEpochMilli() }
                .getOrDefault(System.currentTimeMillis())
        }
    }
}
