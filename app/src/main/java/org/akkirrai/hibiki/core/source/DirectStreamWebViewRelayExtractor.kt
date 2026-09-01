package org.akkirrai.hibiki.core.source

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.akkirrai.beakokit.api.FallbackStreamExtractor
import org.akkirrai.beakokit.http.isAbsoluteUrl
import org.akkirrai.beakokit.http.originOf
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.StreamValidationResult
import org.akkirrai.beakokit.model.VideoStream
import org.akkirrai.hibiki.core.log.AppLogger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android fallback transport for direct streams rejected by a normal HTTP client.
 *
 * It creates a tiny same-origin browser document, imports source-provided cookies, and leaves the
 * document alive as [WebViewStreamRelay]'s Chromium fetch backend. The normal direct extractors
 * remain first, so no WebView is created for streams that validate normally.
 */
internal class DirectStreamWebViewRelayExtractor(
    private val context: Context,
) : FallbackStreamExtractor {
    override fun supports(link: PlayerLink): Boolean =
        link.type == PlayerType.DIRECT_HLS || link.type == PlayerType.DIRECT_MP4

    override fun shouldAttempt(
        link: PlayerLink,
        validationFailures: List<StreamValidationResult>,
    ): Boolean = DirectRelayFallbackPolicy.shouldRelay(validationFailures)

    override suspend fun extract(link: PlayerLink): VideoStream = extractVariants(link).first()

    override suspend fun extractVariants(link: PlayerLink): List<VideoStream> {
        val session = prepareSession(link)
        val resourceHeaders = withContext(Dispatchers.Main) {
            buildMap {
                put(link.url, relayHeaders(link.url, link.headers))
                link.audioUrl?.let { put(it, relayHeaders(it, link.audioHeaders.ifEmpty { link.headers })) }
                link.subtitles.forEach { subtitle ->
                    put(subtitle.url, relayHeaders(subtitle.url, subtitle.headers.ifEmpty { link.headers }))
                }
            }
        }
        val token = try {
            withContext(Dispatchers.IO) {
                WebViewStreamRelay.register(
                    webView = session.webView,
                    handler = session.handler,
                    headers = resourceHeaders.getValue(link.url),
                    initialUrls = resourceHeaders.keys,
                    resourceHeaders = resourceHeaders,
                    streaming = true,
                )
            }
        } catch (error: CancellationException) {
            WebViewStreamRelay.discard(session.webView, session.handler)
            throw error
        } catch (error: Throwable) {
            WebViewStreamRelay.discard(session.webView, session.handler)
            throw error
        }
        AppLogger.d(TAG, "Direct relay ready: type=${link.type}, origin=${originOf(link.url)}")
        return listOf(
            VideoStream(
                url = WebViewStreamRelay.proxyUrl(token, link.url),
                type = when (link.type) {
                    PlayerType.DIRECT_HLS -> StreamType.HLS
                    PlayerType.DIRECT_MP4 -> StreamType.MP4
                    PlayerType.EMBED -> error("Embed links are not direct relay inputs")
                },
                quality = link.quality,
                headers = emptyMap(),
                audioUrl = link.audioUrl?.let { WebViewStreamRelay.proxyUrl(token, it) },
                audioHeaders = emptyMap(),
                subtitles = link.subtitles.map { subtitle ->
                    subtitle.copy(
                        url = WebViewStreamRelay.proxyUrl(token, subtitle.url),
                        headers = emptyMap(),
                    )
                },
            ),
        )
    }

    private fun relayHeaders(url: String, headers: Map<String, String>): Map<String, String> = buildMap {
        putAll(headers)
        putIfAbsent("User-Agent", CHROME_USER_AGENT)
        CookieManager.getInstance().getCookie(url)
            ?.takeIf(String::isNotBlank)
            ?.let { put("Cookie", it) }
    }

    private suspend fun prepareSession(link: PlayerLink): CaptureSession = withContext(Dispatchers.Main) {
        withTimeout(PREPARE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                val resources = buildList {
                    add(link.url to link.headers)
                    link.audioUrl?.let { add(it to link.audioHeaders.ifEmpty { link.headers }) }
                    link.subtitles.forEach { add(it.url to it.headers.ifEmpty { link.headers }) }
                }
                resources.forEach { (url, headers) ->
                    headers.entries
                        .firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
                        ?.value
                        ?.split(';')
                        ?.map(String::trim)
                        ?.filter(String::isNotBlank)
                        ?.forEach { cookie -> cookieManager.setCookie(url, cookie) }
                    headers.entries
                        .firstOrNull { it.key.equals("Referer", ignoreCase = true) }
                        ?.value
                        ?.takeIf(::isAbsoluteUrl)
                        ?.let { referer ->
                            headers.entries
                                .firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
                                ?.value
                                ?.split(';')
                                ?.map(String::trim)
                                ?.filter(String::isNotBlank)
                                ?.forEach { cookie -> cookieManager.setCookie(referer, cookie) }
                        }
                    }
                cookieManager.flush()

                val referer = refererUrl(link)
                val userAgent = link.headers.entries
                    .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
                    ?.value
                    ?.takeIf(String::isNotBlank)
                    ?: CHROME_USER_AGENT
                var webView: WebView? = null
                var delivered = false

                fun finish(result: Result<CaptureSession>) {
                    if (delivered || !continuation.isActive) return
                    delivered = true
                    result.fold(continuation::resume, continuation::resumeWithException)
                }

                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = userAgent
                    WebViewStreamRelay.installBridge(this)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            finish(Result.success(CaptureSession(view, handler)))
                        }
                    }
                    loadDataWithBaseURL(
                        referer,
                        RELAY_DOCUMENT,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
                continuation.invokeOnCancellation {
                    webView?.let { current -> handler.post { current.destroy() } }
                }
            }
        }
    }

    private fun refererUrl(link: PlayerLink): String = link.headers.entries
        .firstOrNull { it.key.equals("Referer", ignoreCase = true) }
        ?.value
        ?.takeIf(::isAbsoluteUrl)
        ?: "${originOf(link.url)}/"

    private companion object {
        const val TAG = "DirectStreamRelay"
        const val PREPARE_TIMEOUT_MS = 8_000L
        const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
        const val RELAY_DOCUMENT = "<html><head></head><body></body></html>"
    }
}

/** Pure fallback policy kept separate from Android so failure classification is unit-testable. */
internal object DirectRelayFallbackPolicy {
    fun shouldRelay(failures: List<StreamValidationResult>): Boolean =
        failures.isEmpty() || failures.any { failure ->
            val status = failure.statusCode
            status == null ||
                status == 401 ||
                status == 403 ||
                status == 408 ||
                status == 425 ||
                status == 429 ||
                status in 500..599 ||
                status in 200..299
        }
}
