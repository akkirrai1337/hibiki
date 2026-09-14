package org.akkirrai.hibiki.core.source

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.akkirrai.beakokit.api.BrowserRelayProvider
import org.akkirrai.beakokit.api.BrowserRelayRequest
import org.akkirrai.beakokit.api.BrowserRelayResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Backs [BrowserRelayProvider] with [WebViewStreamRelay]: navigates a WebView to the resolver's
 * page URL for a genuine, browser-computed Referer/Origin identity - a blank `loadDataWithBaseURL()`
 * document does not get one no matter what `baseUrl` is passed (confirmed on-device against
 * megaplay.buzz's CDN, which 403'd a relayed request from such a document and accepted the
 * identical request once the same WebView had actually navigated there) - then stops the load
 * immediately after it commits, before the page's own scripts/ads/autoplay run, and registers the
 * caller's URLs with [WebViewStreamRelay].
 *
 * Every call opens its own WebView/relay session, unlike [AndroidBrowserFetchProvider][org.akkirrai.hibiki.core.network.AndroidBrowserFetchProvider]
 * which reuses one page across same-URL one-shot requests: a relay session is meant to live for the
 * whole playback, and is discarded by the caller (a resolver's [org.akkirrai.beakokit.api.StreamExtractor])
 * once no longer needed, not by this provider.
 */
class AndroidBrowserRelayProvider(
    private val context: Context,
) : BrowserRelayProvider {
    override suspend fun relay(request: BrowserRelayRequest): BrowserRelayResult {
        val session = prepareSession(request.pageUrl, request.headers)
        val resourceHeaders = request.urls.associateWith { emptyMap<String, String>() }
        try {
            val token = withContext(Dispatchers.IO) {
                WebViewStreamRelay.register(
                    webView = session.webView,
                    handler = session.handler,
                    headers = emptyMap(),
                    initialUrls = resourceHeaders.keys,
                    resourceHeaders = resourceHeaders,
                    streaming = true,
                )
            }
            return BrowserRelayResult(
                urls = request.urls.associateWith { url -> WebViewStreamRelay.proxyUrl(token, url) },
            )
        } catch (error: Throwable) {
            WebViewStreamRelay.discard(session.webView, session.handler)
            throw error
        }
    }

    private suspend fun prepareSession(pageUrl: String, headers: Map<String, String>): CaptureSession =
        withContext(Dispatchers.Main) {
            withTimeout(SESSION_PREPARE_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val handler = Handler(Looper.getMainLooper())
                    var delivered = false
                    fun finish(result: Result<CaptureSession>) {
                        if (delivered || !continuation.isActive) return
                        delivered = true
                        result.fold(continuation::resume, continuation::resumeWithException)
                    }
                    val webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.mediaPlaybackRequiresUserGesture = false
                        // The page's own scripts never run long enough to need images - pure
                        // wasted bandwidth on the connection this exists to go easy on.
                        settings.loadsImagesAutomatically = false
                        settings.blockNetworkImage = true
                        settings.userAgentString = headers.entries
                            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
                            ?.value
                            ?.takeIf(String::isNotBlank)
                            ?: CHROME_USER_AGENT
                        WebViewStreamRelay.installBridge(this)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                // The navigation - and with it the document's Referer/Origin
                                // identity - is already committed; stopping here keeps this WebView
                                // from spending any more of the connection on the page itself.
                                view.stopLoading()
                                finish(Result.success(CaptureSession(view, handler)))
                            }
                        }
                        loadUrl(pageUrl, headers)
                    }
                    continuation.invokeOnCancellation {
                        handler.post { webView.destroyAndClearData() }
                    }
                }
            }
        }

    private companion object {
        const val SESSION_PREPARE_TIMEOUT_MS = 5_000L
        const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
    }
}
