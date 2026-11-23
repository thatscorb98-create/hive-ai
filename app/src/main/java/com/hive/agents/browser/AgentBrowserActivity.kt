package com.hive.agents.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hive.agents.agent.HiveGraph
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.HiveTheme
import com.hive.agents.ui.theme.Ink
import com.hive.agents.ui.theme.Teal
import com.hive.agents.ui.theme.TextDim

/**
 * One browser window per bot.
 *
 * The human can use this window exactly like a normal browser — sign in, solve a CAPTCHA,
 * finish a checkout — and the bot keeps that session and drives the same window with its tools.
 */
class AgentBrowserActivity : ComponentActivity() {

    /** A URL pushed by a second open request while this window already exists. */
    private val incomingUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val agentId = intent.getStringExtra(EXTRA_AGENT_ID).orEmpty()
        val agentName = intent.getStringExtra(EXTRA_AGENT_NAME).orEmpty()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()

        setContent {
            HiveTheme {
                BrowserScreen(
                    agentId = agentId,
                    agentName = agentName.ifBlank { "Bot" },
                    startUrl = url.ifBlank { "https://www.google.com" },
                    incomingUrl = incomingUrl,
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isNotBlank()) incomingUrl.value = url
    }

    companion object {
        const val EXTRA_AGENT_ID = "agent_id"
        const val EXTRA_AGENT_NAME = "agent_name"
        const val EXTRA_URL = "url"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserScreen(
    agentId: String,
    agentName: String,
    startUrl: String,
    incomingUrl: MutableState<String?>,
    onClose: () -> Unit
) {
    var urlBar by remember { mutableStateOf(startUrl) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf("") }
    var handedBack by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportMultipleWindows(false)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                    urlBar = url ?: urlBar
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                    loading = newProgress < 100
                }
            }
            loadUrl(startUrl)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    /*
     * Attach on every resume, not just on first composition. Returning to a window that was
     * already open otherwise leaves the hub holding a stopped view (or another bot's window),
     * and the bot would drive the wrong page.
     */
    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> BrowserHub.attach(agentId, webView)
                Lifecycle.Event.ON_DESTROY -> BrowserHub.detach(webView)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        BrowserHub.attach(agentId, webView)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            BrowserHub.detach(webView)
        }
    }

    // A second open request for this window reuses it and loads the new address.
    LaunchedEffect(incomingUrl.value) {
        val next = incomingUrl.value ?: return@LaunchedEffect
        incomingUrl.value = null
        urlBar = next
        webView.loadUrl(next)
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("$agentName's browser", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Amber)
                Text(
                    "You are in control. Sign in or finish any step, then hand it back so the bot can continue.",
                    fontSize = 10.sp,
                    color = TextDim
                )
            }
            TextButton(onClick = onClose) { Text("Close", fontSize = 12.sp) }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = urlBar,
                onValueChange = { urlBar = it },
                singleLine = true,
                label = { Text("Address", fontSize = 11.sp) },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                error = ""
                val target = if (urlBar.startsWith("http")) urlBar else "https://$urlBar"
                runCatching { webView.loadUrl(target) }
                    .onFailure { error = it.message ?: "Could not open that address" }
            }) { Text("Go") }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(onClick = { if (webView.canGoBack()) webView.goBack() }) { Text("< Back", fontSize = 11.sp) }
            TextButton(onClick = { if (webView.canGoForward()) webView.goForward() }) { Text("Forward", fontSize = 11.sp) }
            TextButton(onClick = { webView.reload() }) { Text("Reload", fontSize = 11.sp) }
            TextButton(onClick = {
                val graph = HiveGraph.get(context.applicationContext)
                val agent = graph.store.snapshot().agent(agentId)
                if (agent != null) {
                    graph.engine.say(
                        agent,
                        "I finished in the browser and handed it back to you. Continue from here.",
                        fromUser = true
                    )
                }
                handedBack = true
            }) {
                Text(if (handedBack) "Bot notified" else "Hand back to bot", fontSize = 11.sp, color = Teal)
            }
        }

        if (loading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = Good
            )
        }

        if (error.isNotBlank()) {
            Text(error, fontSize = 11.sp, color = Amber, modifier = Modifier.padding(horizontal = 12.dp))
        }

        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}
