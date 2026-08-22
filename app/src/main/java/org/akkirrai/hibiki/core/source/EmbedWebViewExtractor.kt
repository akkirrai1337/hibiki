package org.akkirrai.hibiki.core.source

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.http.normalizeUrl
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoStream
import kotlin.coroutines.resume

/**
 * Last-resort resolver for any source's EMBED player link: loads the page in a real WebView and
 * sniffs the HLS/DASH manifest request it ends up making, instead of every source needing its
 * own host-specific extractor. Many embed pages are JS-driven and either sign their manifest URL
 * per request or gate it behind a check only a real browser engine satisfies (TLS/JA3
 * fingerprinting, hydration that only runs in a visible page, etc.) -- scraping them server-side
 * from the source extension is either impossible or unreliable, so this is the generic fallback
 * a source can rely on simply by returning `PlayerType.EMBED` links.
 *
 * Registered after every host-specific [StreamExtractor] (see [commonPlaybackExtractors] and the
 * per-app `additionalExtractors` lists), so it only runs when nothing more specific claims the
 * link first.
 */
class EmbedWebViewExtractor(
    private val context: Context,
) : StreamExtractor {
    override fun supports(link: PlayerLink): Boolean = link.type == PlayerType.EMBED

    override suspend fun extract(link: PlayerLink): VideoStream = withContext(Dispatchers.Main) {
        val playerUrl = normalizeUrl(link.url)
        val captured = capture(playerUrl, link.headers)
            ?: throw SourceException("Embed player did not expose a video stream: $playerUrl")
        VideoStream(
            url = captured.url,
            type = captured.type,
            quality = link.quality,
            headers = captured.headers,
        )
    }

    private suspend fun capture(
        playerUrl: String,
        pageHeaders: Map<String, String>,
    ): CapturedStream? = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        val fallbackReferer = pageHeaders.entries
            .firstOrNull { it.key.equals("Referer", ignoreCase = true) || it.key.equals("Referrer", ignoreCase = true) }
            ?.value
            ?.takeIf(String::isNotBlank)
            ?: playerUrl
        var webView: WebView? = null
        var delivered = false
        var captured: CapturedStream? = null
        var settle: Runnable? = null

        fun destroy() {
            fun destroyOnMain() {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                destroyOnMain()
            } else {
                handler.post(::destroyOnMain)
            }
        }

        fun deliver(result: CapturedStream?) {
            if (delivered) return
            delivered = true
            settle?.let(handler::removeCallbacks)
            handler.removeCallbacksAndMessages(TIMEOUT_TOKEN)
            destroy()
            if (continuation.isActive) continuation.resume(result)
        }

        fun captureStream(request: WebResourceRequest, type: StreamType) {
            val headers = LinkedHashMap<String, String>()
            request.requestHeaders.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) headers[name] = value
            }
            headers.removeCaseInsensitive("Referer")
            headers.removeCaseInsensitive("Referrer")
            headers.removeCaseInsensitive("User-Agent")
            val requestReferer = request.requestHeaders.entries
                .firstOrNull { it.key.equals("Referer", ignoreCase = true) || it.key.equals("Referrer", ignoreCase = true) }
                ?.value
                ?.takeIf(String::isNotBlank)
                ?: fallbackReferer
            headers["Referer"] = requestReferer
            headers["User-Agent"] = CHROME_USER_AGENT
            val cookie = CookieManager.getInstance().getCookie(request.url.toString())
                ?: CookieManager.getInstance().getCookie(playerUrl)
            if (!cookie.isNullOrBlank()) headers["Cookie"] = cookie
            captured = CapturedStream(request.url.toString(), type, headers)
            settle?.let(handler::removeCallbacks)
            settle = Runnable { deliver(captured) }.also { handler.postDelayed(it, SETTLE_DELAY_MS) }
        }

        val timeout = Runnable { deliver(captured) }
        handler.postAtTime(timeout, TIMEOUT_TOKEN, android.os.SystemClock.uptimeMillis() + TIMEOUT_MS)

        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = CHROME_USER_AGENT
            @Suppress("DEPRECATION")
            settings.allowFileAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val path = request.url.toString().substringBefore('?')
                    when {
                        path.endsWith(".m3u8", ignoreCase = true) -> handler.post { captureStream(request, StreamType.HLS) }
                        path.endsWith(".mpd", ignoreCase = true) -> handler.post { captureStream(request, StreamType.DASH) }
                        path.endsWith(".mp4", ignoreCase = true) -> handler.post { captureStream(request, StreamType.MP4) }
                    }
                    return null
                }

                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(PLAY_SCRIPT, null)
                }
            }
            loadUrl(playerUrl, pageHeaders)
        }

        continuation.invokeOnCancellation {
            settle?.let(handler::removeCallbacks)
            handler.removeCallbacksAndMessages(TIMEOUT_TOKEN)
            destroy()
        }
    }

    private fun MutableMap<String, String>.removeCaseInsensitive(name: String) {
        keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(::remove)
    }

    private data class CapturedStream(val url: String, val type: StreamType, val headers: Map<String, String>)

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val SETTLE_DELAY_MS = 1_500L
        const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
        val TIMEOUT_TOKEN = Any()
        const val PLAY_SCRIPT = """
            (function() {
                try {
                    var video = document.querySelector('video');
                    if (video) { video.muted = true; video.play().catch(function(){}); }
                    var button = document.querySelector('.vjs-big-play-button, .plyr__control--overlaid, button[aria-label*=Play]');
                    if (button) button.click();
                } catch (e) {}
            })();
        """
    }
}
