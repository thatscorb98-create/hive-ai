package com.hive.agents.agent

import com.hive.agents.data.HiveStore
import com.hive.agents.net.NimClient
import org.json.JSONObject

/**
 * The proofreader gate.
 *
 * Every outbound email and every piece of sales copy is rewritten here before it leaves the
 * building, so the company does not send obvious AI slop. It keeps facts, prices and links
 * exactly as the agent wrote them, and it never invents a customer, result or deadline. If the
 * model is unavailable or returns something unusable, the original text is sent unchanged
 * rather than an empty message.
 */
class Proofreader(
    private val nim: NimClient,
    private val store: HiveStore
) {

    private val enabled: Boolean get() = store.snapshot().settings.proofreadOutbound

    suspend fun outbound(subject: String, body: String): Pair<String, String> {
        if (!enabled || body.isBlank()) return subject to body
        val reply = nim.chat(
            listOf(
                NimClient.Msg("system", EMAIL_RULES),
                NimClient.Msg("user", "Subject: $subject\n\nDraft:\n$body")
            ),
            model = store.snapshot().settings.model,
            maxTokens = 900,
            temperature = 0.2
        )
        val parsed = parse(reply.content) ?: return subject to body
        val cleanSubject = parsed.optString("subject").trim()
        val cleanBody = parsed.optString("body").trim()
        if (cleanBody.isBlank()) return subject to body
        return cleanSubject.ifBlank { subject } to cleanBody
    }

    suspend fun copy(text: String, what: String): String {
        if (!enabled || text.isBlank()) return text
        val reply = nim.chat(
            listOf(
                NimClient.Msg("system", COPY_RULES),
                NimClient.Msg("user", "This is the $what:\n\n$text")
            ),
            model = store.snapshot().settings.model,
            maxTokens = 700,
            temperature = 0.2
        )
        val parsed = parse(reply.content) ?: return text
        return parsed.optString("text").trim().ifBlank { text }
    }

    private fun parse(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
    }

    private companion object {
        val EMAIL_RULES = """
            You are the proofreader at Local Reach Workshop. Rewrite the draft so it reads like
            a sharp human wrote it, not a chatbot.

            Rules:
            - Keep every fact, number, price, name, link and promise exactly as written.
            - Never invent a client, result, testimonial, discount, deadline or personal detail.
            - Cut AI tells: "I hope this email finds you well", "in today's fast-paced world",
              "delve", "elevate", "unlock", "seamless", "game-changer", "we are excited to",
              em-dash chains, emoji, exclamation stacks and filler openers.
            - Use short sentences and plain words. One clear ask.
            - Keep the body under 160 words.

            Reply with JSON only, no markdown:
            {"subject":"...","body":"..."}
        """.trimIndent()

        val COPY_RULES = """
            You are the proofreader at Local Reach Workshop. Rewrite the copy so it reads like a
            competent human wrote it for a local business owner.

            Rules:
            - Keep every fact, number, price, name and link exactly as written.
            - Never invent a result, client, guarantee or statistic.
            - Remove AI tells: "delve", "elevate", "unlock", "seamless", "game-changer",
              "in today's fast-paced world", em-dash chains, emoji and empty hype.
            - Be specific and concrete. No more than 90 words.

            Reply with JSON only, no markdown:
            {"text":"..."}
        """.trimIndent()
    }
}
