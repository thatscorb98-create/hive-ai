package com.hive.agents.agent

import com.hive.agents.net.Http

/** Web research tools, kept out of the registry so each file stays reviewable. */
object ToolWeb {

    suspend fun fetchPage(url: String): String {
        if (url.isBlank()) return "No URL given."
        val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
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

    suspend fun search(query: String): String {
        if (query.isBlank()) return "Empty query."
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        val headers = mapOf(
            "User-Agent" to ua,
            "Accept-Language" to "en-US,en;q=0.9"
        )

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
