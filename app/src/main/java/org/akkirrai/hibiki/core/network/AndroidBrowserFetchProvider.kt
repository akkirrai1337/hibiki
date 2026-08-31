package org.akkirrai.hibiki.core.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.akkirrai.beakokit.api.BrowserFetchProvider
import org.akkirrai.beakokit.api.BrowserFetchRequest
import org.akkirrai.beakokit.api.BrowserFetchResponse
import org.akkirrai.beakokit.api.SourceUnavailableException
import org.akkirrai.hibiki.core.log.AppLogger
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs HTTP requests from inside a real, headless WebView page's own JS context - for a site whose
 * bot-management binds a solved challenge to the exact client that solved it (TLS/JA3 fingerprint,
 * not just a cookie), a cookie harvested via [AndroidChallengeSessionProvider] and replayed through
 * a separate OkHttp/Ktor client can still be rejected outright. [fetch] loads
 * [BrowserFetchRequest.pageUrl] first (a real page load, not the target endpoint itself - a
 * top-level navigation straight to a JSON API with a huge opaque query string reads as
 * attack-shaped traffic to some WAFs and gets hard-blocked instead of challenged), then performs
 * `fetch(targetUrl, ...)` from within that loaded page: a genuine same-stack request, not a replay.
 *
 * The loaded page is kept alive and reused across calls that share the same [BrowserFetchRequest.pageUrl]
 * (a page reload per call would be needlessly slow for a source making several requests per screen,
 * e.g. a catalog scroll) - it's only reloaded when a caller asks for a different page or the
 * previous WebView died. [Mutex] serializes access since a single WebView isn't safe for concurrent
 * script injection.
 *
 * Headless by design (unlike [ChallengeSessionActivity]'s visible verification screen): this is for
 * sites that never show an interactive challenge to solve, just gate on request fingerprint - there
 * is nothing for a user to see or do.
 */
class AndroidBrowserFetchProvider(
    private val context: Context,
) : BrowserFetchProvider {
    private val mutex = Mutex()
    private var session: PageSession? = null
    private val pending = ConcurrentHashMap<String, PendingCall>()

    override suspend fun fetch(request: BrowserFetchRequest): BrowserFetchResponse = mutex.withLock {
        val webView = currentSession(request.pageUrl)
        runFetch(webView, request)
    }

    private suspend fun currentSession(pageUrl: String): WebView {
        val existing = session
        if (existing != null && existing.pageUrl == pageUrl && !existing.destroyed) {
            return existing.webView
        }
        existing?.destroyed = true
        existing?.let { destroyed -> onMain { destroyed.webView.destroy() } }
        val webView = loadPage(pageUrl)
        session = PageSession(pageUrl, webView)
        return webView
    }

    private suspend fun loadPage(pageUrl: String): WebView = suspendCancellableCoroutine { continuation ->
        onMain {
            var delivered = false
            lateinit var timeout: Runnable
            val handler = Handler(Looper.getMainLooper())
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = CHROME_USER_AGENT
                addJavascriptInterface(FetchBridge(handler), BRIDGE_NAME)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (delivered) return
                        delivered = true
                        handler.removeCallbacks(timeout)
                        AppLogger.d(TAG, "Page loaded: $url")
                        if (continuation.isActive) continuation.resume(view)
                    }
                }
            }
            timeout = Runnable {
                if (delivered) return@Runnable
                delivered = true
                AppLogger.w(TAG, "Page load timed out: $pageUrl")
                webView.destroy()
                if (continuation.isActive) {
                    continuation.resumeWithException(SourceUnavailableException("Browser page load timed out for $pageUrl"))
                }
            }
            handler.postDelayed(timeout, PAGE_LOAD_TIMEOUT_MS)
            webView.loadUrl(pageUrl)
            continuation.invokeOnCancellation {
                if (!delivered) onMain { webView.destroy() }
            }
        }
    }

    private suspend fun runFetch(webView: WebView, request: BrowserFetchRequest): BrowserFetchResponse =
        suspendCancellableCoroutine { continuation ->
            val callId = UUID.randomUUID().toString()
            val handler = Handler(Looper.getMainLooper())
            var delivered = false
            lateinit var timeout: Runnable

            fun complete(result: Result<BrowserFetchResponse>) {
                if (delivered) return
                delivered = true
                handler.removeCallbacks(timeout)
                pending.remove(callId)
                if (continuation.isActive) {
                    result.fold(onSuccess = continuation::resume, onFailure = continuation::resumeWithException)
                }
            }

            pending[callId] = PendingCall(
                onResult = { status, body, headers -> handler.post { complete(Result.success(BrowserFetchResponse(status, body, headers))) } },
                onError = { message -> handler.post { complete(Result.failure(SourceUnavailableException("Browser fetch failed: $message"))) } },
            )
            timeout = Runnable {
                AppLogger.w(TAG, "In-page fetch timed out: target=${request.targetUrl}")
                complete(Result.failure(SourceUnavailableException("Browser fetch timed out for ${request.targetUrl}")))
            }
            handler.postDelayed(timeout, FETCH_TIMEOUT_MS)
            onMain { webView.evaluateJavascript(buildFetchScript(callId, request), null) }
            continuation.invokeOnCancellation { pending.remove(callId) }
        }

    private fun buildFetchScript(callId: String, request: BrowserFetchRequest): String {
        val options = JSONObject().apply {
            put("method", request.method)
            put("credentials", "include")
            put("headers", JSONObject(request.headers))
            request.body?.let { put("body", it) }
        }
        val targetUrlJson = JSONObject.quote(request.targetUrl)
        val callIdJson = JSONObject.quote(callId)
        return """
            (function() {
                fetch($targetUrlJson, $options).then(function(response) {
                    var headers = {};
                    response.headers.forEach(function(value, key) { headers[key] = value; });
                    return response.text().then(function(body) {
                        $BRIDGE_NAME.onResult($callIdJson, response.status, body, JSON.stringify(headers));
                    });
                }).catch(function(error) {
                    $BRIDGE_NAME.onError($callIdJson, String(error));
                });
            })();
        """.trimIndent()
    }

    private inner class FetchBridge(private val handler: Handler) {
        @JavascriptInterface
        fun onResult(callId: String, status: Int, body: String, headersJson: String) {
            handler.post {
                val call = pending[callId] ?: return@post
                val headers = buildMap {
                    val json = JSONObject(headersJson)
                    json.keys().forEach { key -> put(key, json.getString(key)) }
                }
                call.onResult(status, body, headers)
            }
        }

        @JavascriptInterface
        fun onError(callId: String, message: String) {
            handler.post { pending[callId]?.onError?.invoke(message) }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
    }

    private class PageSession(val pageUrl: String, val webView: WebView) {
        @Volatile var destroyed: Boolean = false
    }

    private class PendingCall(
        val onResult: (Int, String, Map<String, String>) -> Unit,
        val onError: (String) -> Unit,
    )

    private companion object {
        const val TAG = "BrowserFetch"
        const val PAGE_LOAD_TIMEOUT_MS = 20_000L
        const val FETCH_TIMEOUT_MS = 20_000L
        const val BRIDGE_NAME = "HibikiBrowserFetchBridge"
        const val CHROME_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
    }
}
