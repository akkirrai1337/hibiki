package org.akkirrai.hibiki.core.source

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.http.hostOf
import org.akkirrai.beakokit.http.normalizeUrl
import org.akkirrai.beakokit.http.originOf
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoStream
import kotlin.coroutines.resume

/** Resolves AnimePahe's Megaplay and Vidwish embeds in Android's browser engine. */
class AnimePaheWebViewExtractor(
    private val context: Context,
) : StreamExtractor {
    override fun supports(link: PlayerLink): Boolean = isAnimePahePlayerLink(link)

    override suspend fun extract(link: PlayerLink): VideoStream = withContext(Dispatchers.Main) {
        val playerUrl = normalizeUrl(link.url)
        val captured = capture(playerUrl, link.headers)
            ?: throw SourceException("AnimePahe player did not expose a video stream")
        VideoStream(
            url = captured.url,
            type = StreamType.HLS,
            quality = link.quality,
            headers = captured.headers,
        )
    }

    private suspend fun capture(
        playerUrl: String,
        pageHeaders: Map<String, String>,
    ): CapturedStream? = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        val playbackReferer = "${originOf(playerUrl).trimEnd('/')}/"
        var webView: WebView? = null
        var delivered = false
        var captured: CapturedStream? = null
        var settle: Runnable? = null

        fun destroy() {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }

        fun deliver(result: CapturedStream?) {
            if (delivered) return
            delivered = true
            settle?.let(handler::removeCallbacks)
            handler.removeCallbacksAndMessages(TIMEOUT_TOKEN)
            destroy()
            if (continuation.isActive) continuation.resume(result)
        }

        fun captureStream(request: WebResourceRequest) {
            val headers = LinkedHashMap<String, String>()
            request.requestHeaders.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) headers[name] = value
            }
            headers.removeCaseInsensitive("Referer")
            headers.removeCaseInsensitive("Referrer")
            headers.removeCaseInsensitive("User-Agent")
            headers["Referer"] = playbackReferer
            headers["User-Agent"] = CHROME_USER_AGENT
            captured = CapturedStream(request.url.toString(), headers)
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
                    if (request.url.toString().substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
                        handler.post { captureStream(request) }
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

    private data class CapturedStream(val url: String, val headers: Map<String, String>)

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

internal fun isAnimePahePlayerLink(link: PlayerLink): Boolean {
    if (link.type != PlayerType.EMBED) return false
    val host = hostOf(link.url)?.lowercase().orEmpty()
    return host == "megaplay.buzz" || host.endsWith(".megaplay.buzz") ||
        host == "vidwish.live" || host.endsWith(".vidwish.live")
}
