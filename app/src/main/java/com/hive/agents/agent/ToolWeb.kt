package com.hive.agents.agent

import com.hive.agents.net.Http
import org.json.JSONArray
import org.json.JSONObject

/** Web research tools, kept out of the registry so each file stays reviewable. */
object ToolWeb {

    suspend fun fetchPage(url: String): String {
        if (url.isBlank()) return "No URL given."
        val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        if (!isSafePublicUrl(target)) {
            return "That address is private or is not a public http(s) URL, so it was not fetched."
        }
        val raw = runCatching { Http.get(target) }.getOrElse { return "Fetch failed: ${it.message}" }
        val text = raw
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?s)<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
        return text.take(6000).ifBlank { "No readable text on that page." }
    }

    /**
     * Google first when the owner has added a Programmable Search key, then DuckDuckGo and Bing
     * RSS. Google has no free scraping endpoint: it needs a Custom Search JSON API key and a
     * search engine id, which is why this is an optional pair of vault keys.
     */
    suspend fun search(query: String, googleKey: String = "", googleCx: String = ""): String {
        if (query.isBlank()) return "Empty query."
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        val headers = mapOf(
            "User-Agent" to ua,
            "Accept-Language" to "en-US,en;q=0.9"
        )

        if (googleKey.isNotBlank() && googleCx.isNotBlank()) {
            val googleHits = google(encoded, googleKey, googleCx)
            if (googleHits.isNotEmpty()) return googleHits.joinToString("\n")
        }

        // DuckDuckGo sometimes answers with a 202 challenge and no result markup. Do not treat
        // that as "no results": fall through to Bing's RSS feed, which is stable and cheap.
        val duck = runCatching {
            Http.get("https://html.duckduckgo.com/html/?q=$encoded", headers)
        }.getOrNull().orEmpty()
        val duckHits = Regex(
            """result__a[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL
        ).findAll(duck).take(5).map { match ->
            val link = match.groupValues[1]
            val title = match.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            "- $title ($link)"
        }.toList()
        if (duckHits.isNotEmpty()) return duckHits.joinToString("\n")

        val bing = try {
            Http.get("https://www.bing.com/search?format=rss&q=$encoded", headers)
        } catch (error: Exception) {
            return "Search failed: ${error.message?.take(160) ?: "unknown error"}"
        }
        val bingHits = Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(bing)
            .take(5)
            .mapNotNull { item ->
                val block = item.groupValues[1]
                val title = cleanXml(
                    Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
                        .find(block)?.groupValues?.get(1).orEmpty()
                )
                val link = cleanXml(
                    Regex("""<link>(.*?)</link>""", RegexOption.DOT_MATCHES_ALL)
                        .find(block)?.groupValues?.get(1).orEmpty()
                )
                if (title.isBlank() || link.isBlank()) return@mapNotNull null
                val snippet = cleanXml(
                    Regex("""<description>(.*?)</description>""", RegexOption.DOT_MATCHES_ALL)
                        .find(block)?.groupValues?.get(1).orEmpty()
                ).take(180)
                if (snippet.isBlank()) "- $title ($link)" else "- $title ($link)\n  $snippet"
            }
            .toList()

        return if (bingHits.isEmpty()) "No results for \"$query\"." else bingHits.joinToString("\n")
    }

    /**
     * Blocks localhost, LAN, link-local and other non-public destinations. This is not a full
     * SSRF defence (DNS can change between this check and the request), but it stops an injected
     * page from pointing the agent at the router, a phone-local service or cloud metadata.
     */
    internal fun isSafePublicUrl(url: String): Boolean {
        val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (parsed.scheme?.lowercase() !in listOf("http", "https")) return false
        val host = parsed.host?.removePrefix("[")?.removeSuffix("]")?.lowercase() ?: return false
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return false
        val address = runCatching { java.net.InetAddress.getByName(host) }.getOrNull() ?: return false
        return !(
            address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress
            )
    }

    private suspend fun google(
        encoded: String,
        key: String,
        cx: String
    ): List<String> {
        val url = "https://www.googleapis.com/customsearch/v1" +
            "?key=${java.net.URLEncoder.encode(key, "UTF-8")}" +
            "&cx=${java.net.URLEncoder.encode(cx, "UTF-8")}" +
            "&num=5&q=$encoded"
        val raw = runCatching { Http.get(url) }.getOrNull().orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val items = JSONObject(raw).optJSONArray("items") ?: JSONArray()
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val title = item.optString("title")
                val link = item.optString("link")
                val snippet = item.optString("snippet")
                if (title.isBlank() || link.isBlank()) return@mapNotNull null
                if (snippet.isBlank()) "- $title ($link)" else "- $title ($link)\n  ${snippet.take(180)}"
            }
        }.getOrDefault(emptyList())
    }

    private fun cleanXml(value: String): String = value
        .replace("<![CDATA[", "")
        .replace("]]>", "")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace(Regex("<[^>]+>"), "")
        .trim()

}
