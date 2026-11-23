package com.hive.agents.browser

import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/** A request for the UI to open a bot's browser. */
data class BrowserOpenRequest(
    val agentId: String,
    val agentName: String,
    val url: String,
    val requestedAt: Long = System.currentTimeMillis()
)

/**
 * The bridge between agents and the real WebView they drive.
 *
 * Each bot has its own browser window, URL and history. The agent can navigate, read the page,
 * click by visible text and type into fields, while the human can take the same window and log
 * in or solve a challenge. Anything the agent does not have a tool for, the human can do here.
 */
object BrowserHub {

    private var webView: WebView? = null
    private var activeAgentId: String? = null
    private var lastUrl: String = ""

    private val _pendingOpen = MutableStateFlow<BrowserOpenRequest?>(null)
    val pendingOpen: StateFlow<BrowserOpenRequest?> = _pendingOpen.asStateFlow()

    private val _currentAgent = MutableStateFlow<String?>(null)
    val currentAgent: StateFlow<String?> = _currentAgent.asStateFlow()

    val isOpen: Boolean get() = webView != null

    fun attach(agentId: String, view: WebView) {
        webView = view
        activeAgentId = agentId
        _currentAgent.value = agentId
    }

    fun detach(view: WebView) {
        if (webView === view) {
            webView = null
            activeAgentId = null
            _currentAgent.value = null
        }
    }

    fun rememberUrl(url: String) {
        lastUrl = url
    }

    fun lastUrlFor(agentId: String): String = lastUrl

    /** Called by an agent tool: asks the UI to open the browser for this bot. */
    fun requestOpen(agentId: String, agentName: String, url: String) {
        _pendingOpen.value = BrowserOpenRequest(agentId, agentName, url)
    }

    fun clearRequest() {
        _pendingOpen.value = null
    }

    suspend fun open(url: String): String {
        val view = webView ?: return "The browser for this bot is closed. Open Browsers and pick this bot first."
        runOnUi(view) { it.loadUrl(url) }
        lastUrl = url
        return "Opened $url"
    }

    suspend fun readPage(): String {
        val view = webView ?: return "The browser is closed, so there is no page to read."
        val text = evaluate(view, "document.body ? document.body.innerText.slice(0, 8000) : ''")
        return unquote(text).ifBlank { "The page is empty or still loading." }
    }

    suspend fun click(text: String): String {
        val view = webView ?: return "The browser is closed."
        val needle = JSONObject.quote(text)

        /*
         * Click the first visible clickable element whose label or value contains the text.
         * Deliberately text-based: it works on sites with no API and survives markup changes.
         */
        val js = """
            (function(){
              var t = $needle.toLowerCase();
              var nodes = Array.prototype.slice.call(
                document.querySelectorAll('a,button,input,[role=button],[onclick]')
              );
              for (var i = 0; i < nodes.length; i++) {
                var el = nodes[i];
                var label = ((el.innerText || '') + ' ' + (el.value || '') + ' ' + (el.getAttribute('aria-label') || '')).toLowerCase();
                if (label.indexOf(t) !== -1) {
                  el.scrollIntoView({block: 'center'});
                  el.click();
                  return 'clicked';
                }
              }
              return 'not found';
            })()
        """.trimIndent()
        val result = unquote(evaluate(view, js))
        return if (result == "clicked") "Clicked \"$text\"." else "No clickable element matched \"$text\"."
    }

    suspend fun typeInto(target: String, value: String): String {
        val view = webView ?: return "The browser is closed."
        val needle = JSONObject.quote(target)
        val payload = JSONObject.quote(value)

        val js = """
            (function(){
              var t = $needle.toLowerCase();
              var v = $payload;
              var nodes = Array.prototype.slice.call(document.querySelectorAll('input,textarea'));
              for (var i = 0; i < nodes.length; i++) {
                var el = nodes[i];
                var label = ((el.placeholder || '') + ' ' + (el.name || '') + ' ' + (el.id || '') + ' ' + (el.getAttribute('aria-label') || '')).toLowerCase();
                if (label.indexOf(t) !== -1) {
                  el.focus();
                  el.value = v;
                  el.dispatchEvent(new Event('input', {bubbles: true}));
                  el.dispatchEvent(new Event('change', {bubbles: true}));
                  return 'typed';
                }
              }
              return 'not found';
            })()
        """.trimIndent()
        val result = unquote(evaluate(view, js))
        return if (result == "typed") "Typed into \"$target\"." else "No field matched \"$target\"."
    }

    suspend fun scrollDown(): String {
        val view = webView ?: return "The browser is closed."
        runOnUi(view) { it.scrollBy(0, it.height / 2) }
        return "Scrolled down."
    }

    private suspend fun evaluate(view: WebView, js: String): String =
        suspendCancellableCoroutine { cont ->
            view.post {
                runCatching {
                    view.evaluateJavascript(js) { raw -> cont.resume(raw.orEmpty()) }
                }.onFailure { cont.resume("\"\"") }
            }
        }

    private fun runOnUi(view: WebView, block: (WebView) -> Unit) {
        view.post { runCatching { block(view) } }
    }

    /** evaluateJavascript returns a JSON-encoded string; undo that for display. */
    private fun unquote(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") return ""
        return runCatching { JSONObject("{\"v\":$trimmed}").optString("v") }.getOrElse { trimmed.trim('"') }
    }
}
