package org.akkirrai.hibiki.core.source

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.extension.BrowserScriptResolver
import org.akkirrai.beakokit.http.normalizeUrl
import org.akkirrai.beakokit.http.originOf
import org.akkirrai.beakokit.http.hostOf
import org.akkirrai.beakokit.http.resolveUrl
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.SubtitleTrack
import org.akkirrai.beakokit.model.VideoStream
import org.akkirrai.hibiki.core.log.AppLogger
import kotlin.coroutines.resume

/** Generic Android runtime for BROWSER resolvers. Site behaviour lives only in extensions. */
class BrowserPlayerWebViewExtractor(
    private val context: Context,
    private val resolvers: List<BrowserScriptResolver>,
    private val client: HttpClient,
) : StreamExtractor {
    override fun supports(link: PlayerLink): Boolean = BrowserResolverRouting.supports(link, resolvers)

    override suspend fun extract(link: PlayerLink): VideoStream = extractVariants(link).first()

    override suspend fun extractVariants(link: PlayerLink): List<VideoStream> {
        val resolver = resolvers.firstOrNull { it.supportsBrowser(link) }
            ?: throw SourceException("No browser resolver is installed for this player")
        AppLogger.d(TAG, "Start: host=${hostOf(link.url)}, player=${link.playerName.orEmpty()}")
        val capture = withContext(Dispatchers.Main) {
            capture(normalizeUrl(link.url), link.headers, resolver.browserScript(link))
        }
        val streams = capture.streams
        val session = capture.session
        if (streams.isEmpty()) {
            session?.let { WebViewStreamRelay.discard(it.webView, it.handler) }
            throw SourceException("Browser resolver did not expose a playable stream")
        }
        val ranked = BrowserStreamSelector.rank(streams)
        // The in-page resolver script's fetch() is a browser request and can be silently blocked
        // by CORS on a CDN subdomain that never intended to serve XHR/fetch callers, so it may
        // never observe a site's alternate-audio playlist even though one exists. A native HTTP
        // request from here isn't a browser context and isn't subject to CORS, so it can read the
        // master playlist body and find the real #EXT-X-MEDIA:TYPE=AUDIO URI directly.
        // PlaybackResolver budgets only a few seconds for this whole call (see
        // AnimeWatchRepository's resolveAttemptTimeoutMillis); a CDN that blocks plain HTTP clients
        // (the whole reason WebViewStreamRelay exists) can otherwise eat that entire budget here
        // before ever reaching the relay fallback, silently timing out the attempt with no error.
        val audio = link.audioUrl?.let { url ->
            BrowserCapturedStream(url, link.audioHeaders.ifEmpty { link.headers }, BrowserCaptureOrigin.SOURCE_AUDIO)
        } ?: BrowserStreamSelector.findAudio(streams)
            ?: withTimeoutOrNull(AUDIO_PROBE_TIMEOUT_MS) { ranked.firstNotNullOfOrNull { fetchAudioFromMaster(it) } }
        // Some CDNs behind bot management block a plain HTTP client on TLS fingerprint alone, no
        // matter how closely its headers mimic a browser - the WebView that resolved this link is a
        // genuine Chromium network stack, so it's kept alive as a relay backend and offered as a
        // second candidate per stream. PlaybackResolver tries candidates in order, so the fast
        // direct URL is always attempted first and this fallback only costs anything when needed.
        val capturedSubtitles = capture.subtitles.map { subtitle ->
            SubtitleTrack(
                url = subtitle.url,
                label = subtitle.label,
                language = subtitle.language,
                headers = refreshCookie(subtitle.url, subtitle.headers),
            )
        }
        val subtitles = (
            link.subtitles.map { subtitle ->
                subtitle.copy(headers = refreshCookie(subtitle.url, subtitle.headers.ifEmpty { link.headers }))
            } + capturedSubtitles
        ).distinctBy(SubtitleTrack::url)
        val resourceHeaders = buildMap {
            ranked.forEach { put(it.url, refreshCookie(it.url, it.headers)) }
            audio?.let { put(it.url, refreshCookie(it.url, it.headers)) }
            subtitles.forEach { put(it.url, it.headers) }
        }
        val relayToken = session?.let {
            withContext(Dispatchers.IO) {
                WebViewStreamRelay.register(
                    webView = it.webView,
                    handler = it.handler,
                    headers = resourceHeaders[ranked.first().url].orEmpty(),
                    initialUrls = resourceHeaders.keys,
                    resourceHeaders = resourceHeaders,
                )
            }
        }
        return ranked.flatMap { stream ->
            val direct = VideoStream(
                url = stream.url,
                type = StreamType.HLS,
                quality = qualityFromUrl(stream.url),
                headers = refreshCookie(stream.url, stream.headers),
                audioUrl = audio?.url?.takeUnless { it == stream.url },
                audioHeaders = audio?.let { refreshCookie(it.url, it.headers) }.orEmpty(),
                subtitles = subtitles,
            )
            val proxied = relayToken?.let { token ->
                VideoStream(
                    url = WebViewStreamRelay.proxyUrl(token, stream.url),
                    type = StreamType.HLS,
                    quality = direct.quality,
                    headers = emptyMap(),
                    audioUrl = audio?.url?.takeUnless { it == stream.url }?.let { WebViewStreamRelay.proxyUrl(token, it) },
                    audioHeaders = emptyMap(),
                    subtitles = subtitles.map { subtitle ->
                        subtitle.copy(
                            url = WebViewStreamRelay.proxyUrl(token, subtitle.url),
                            headers = emptyMap(),
                        )
                    },
                )
            }
            listOfNotNull(direct, proxied)
        }
    }

    /**
     * A capture's Cookie header is a snapshot taken the instant its URL was first observed. A
     * Cloudflare (or similar) JS challenge on the embed page can still be resolving at that instant
     * and finish writing its clearance cookie moments later, so the snapshot can predate it even
     * though the WebView's own subsequent requests carry it fine. Re-reading CookieManager right
     * before playback - after the resolver has fully settled - closes that race for any site using
     * this same BROWSER runtime, not just one host's cookie name.
     */
    private fun refreshCookie(url: String, headers: Map<String, String>): Map<String, String> {
        val fresh = CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank) ?: return headers
        if (fresh != headers["Cookie"]) {
            AppLogger.d(TAG, "Cookie changed after settle: host=${hostOf(url)}, hadCookie=${headers.containsKey("Cookie")}")
        }
        return headers + ("Cookie" to fresh)
    }

    private suspend fun fetchAudioFromMaster(candidate: BrowserCapturedStream): BrowserCapturedStream? {
        val playlist = try {
            val response = client.get(candidate.url) {
                candidate.headers.forEach { (name, value) -> header(name, value) }
            }
            if (!response.status.isSuccess()) return null
            response.bodyAsText()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w(TAG, "Native master fetch failed: host=${hostOf(candidate.url)}", error)
            return null
        }
        val audioUri = AUDIO_MEDIA_URI.find(playlist)?.groupValues?.get(1) ?: return null
        return BrowserCapturedStream(resolveUrl(candidate.url, audioUri), candidate.headers, BrowserCaptureOrigin.SOURCE_AUDIO)
    }

    private suspend fun capture(
        pageUrl: String,
        pageHeaders: Map<String, String>,
        resolverScript: String,
    ): BrowserCaptureResult =
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val captures = mutableListOf<BrowserCapturedStream>()
            val subtitles = mutableListOf<BrowserCapturedSubtitle>()
            var webView: WebView? = null
            var delivered = false
            var probes = 0
            var retry: Runnable? = null
            var settle: Runnable? = null
            lateinit var timeout: Runnable

            fun destroyView() {
                val current = webView ?: return
                webView = null
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    current.destroy()
                } else {
                    handler.post { current.destroy() }
                }
            }

            fun finish() {
                if (delivered) return
                delivered = true
                retry?.let(handler::removeCallbacks)
                settle?.let(handler::removeCallbacks)
                handler.removeCallbacks(timeout)
                val result = BrowserStreamSelector.rank(captures)
                AppLogger.d(TAG, "Finish: captured=${captures.size}, candidates=${result.size}")
                // Ownership transfers to the caller instead of destroying here: a blocked direct
                // fetch may still need this same WebView as a relay backend (see WebViewStreamRelay).
                // shouldInterceptRequest is no longer needed once capture is done, and leaving it
                // installed makes WebView buffer every subsequent response for the interception hook
                // - which then leaves the page's own fetch() Promise a Response whose body stream
                // Chromium already drained, surfacing as "body stream already used"/"already read".
                val session = webView?.also { it.webViewClient = WebViewClient() }?.let { CaptureSession(it, handler) }
                webView = null
                if (continuation.isActive) continuation.resume(
                    BrowserCaptureResult(
                        streams = result,
                        subtitles = subtitles.distinctBy(BrowserCapturedSubtitle::url),
                        session = session,
                    ),
                )
            }
            fun add(url: String, headers: Map<String, String>, origin: BrowserCaptureOrigin) {
                if (!BrowserStreamSelector.isHls(url)) return
                // Resolver scripts may poll the browser repeatedly. A duplicate observation must
                // not keep postponing completion forever; only a genuinely new stream does.
                if (captures.any { it.url == url && it.origin == origin }) return
                captures += BrowserCapturedStream(url, playbackHeaders(url, headers, pageUrl, CookieManager.getInstance().getCookie(url)), origin)
                AppLogger.d(TAG, "HLS captured: origin=$origin, host=${hostOf(url)}, path=${url.substringBefore('?').takeLast(120)}, total=${captures.size}")
                // Players commonly request an audio rendition before the video rendition. Wait
                // briefly for that burst to settle, then return the deterministic best candidate
                // before PlaybackResolver's per-player timeout cancels this coroutine.
                settle?.let(handler::removeCallbacks)
                settle = Runnable(::finish).also { handler.postDelayed(it, STREAM_SETTLE_DELAY_MS) }
            }
            fun addSubtitle(url: String, label: String?, language: String?) {
                if (!VTT_URL.matches(url)) return
                if (subtitles.any { it.url == url }) return
                subtitles += BrowserCapturedSubtitle(
                    url = url,
                    label = label?.trim()?.takeIf(String::isNotBlank),
                    language = language?.trim()?.takeIf(String::isNotBlank),
                    headers = playbackHeaders(url, emptyMap(), pageUrl, CookieManager.getInstance().getCookie(url)),
                )
                AppLogger.d(TAG, "Subtitle captured: language=${language.orEmpty()}, host=${hostOf(url)}")
            }
            fun probe(view: WebView) {
                if (delivered || webView !== view || probes++ >= MAX_PROBES) return
                AppLogger.d(TAG, "Run resolver script: attempt=$probes")
                view.evaluateJavascript("$resolverScript\n$VIDEO_ELEMENT_PROBE") { result ->
                    if (result.contains("no-player") || result.contains("error")) {
                        AppLogger.d(TAG, "Resolver script result: $result")
                    }
                }
                retry = Runnable { probe(view) }.also { handler.postDelayed(it, PROBE_DELAY_MS) }
            }

            timeout = Runnable {
                AppLogger.w(TAG, "Timed out: probes=$probes, captured=${captures.size}")
                finish()
            }
            handler.postDelayed(timeout, TIMEOUT_MS)
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.userAgentString = CHROME_USER_AGENT
                addJavascriptInterface(object {
                    @JavascriptInterface fun stream(url: String) = handler.post { add(url, emptyMap(), BrowserCaptureOrigin.VIDEO_ELEMENT) }
                    @JavascriptInterface fun master(url: String) = handler.post { add(url, emptyMap(), BrowserCaptureOrigin.SOURCE_MASTER) }
                    @JavascriptInterface fun video(url: String) = handler.post { add(url, emptyMap(), BrowserCaptureOrigin.SOURCE_VIDEO) }
                    @JavascriptInterface fun audio(url: String) = handler.post { add(url, emptyMap(), BrowserCaptureOrigin.SOURCE_AUDIO) }
                    @JavascriptInterface fun subtitle(url: String, label: String?, language: String?) =
                        handler.post { addSubtitle(url, label, language) }
                    @JavascriptInterface fun done() = handler.post(::finish)
                }, "HibikiResolver")
                // Must run before loadUrl(): a WebView.addJavascriptInterface call only becomes
                // visible to a page's own JS after that page's *next* load, so adding it later (once
                // WebViewStreamRelay.register() actually needs it) would leave the already-loaded
                // page seeing an undefined global instead of the bridge.
                WebViewStreamRelay.installBridge(this)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        handler.post { add(request.url.toString(), request.requestHeaders, BrowserCaptureOrigin.NETWORK) }
                        return null
                    }
                    override fun onPageFinished(view: WebView, url: String) {
                        AppLogger.d(TAG, "Page finished: host=${hostOf(url)}")
                        probe(view)
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                        AppLogger.w(TAG, "WebView request failed: code=${error.errorCode}, main=${request.isForMainFrame}")
                    }
                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                        // Tells us whether the CDN blocks the request even from the WebView's own
                        // Chromium network stack (a real site-side/geo/rate-limit block) or only
                        // from a separate re-fetch elsewhere (headers/TLS-fingerprint mismatch).
                        if (BrowserStreamSelector.isHls(request.url.toString())) {
                            AppLogger.w(TAG, "WebView HTTP error: status=${errorResponse.statusCode}, host=${hostOf(request.url.toString())}")
                        }
                    }
                }
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        AppLogger.d(TAG, "Console: ${message.messageLevel()} ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                        return true
                    }
                }
                loadUrl(pageUrl, pageHeaders)
                handler.postDelayed({ probe(this) }, PROBE_DELAY_MS)
            }
            continuation.invokeOnCancellation {
                AppLogger.w(TAG, "Cancelled: probes=$probes, captured=${captures.size}")
                retry?.let(handler::removeCallbacks)
                settle?.let(handler::removeCallbacks)
                handler.removeCallbacks(timeout)
                destroyView()
            }
        }

    /**
     * WebViewClient.shouldInterceptRequest only exposes headers the *page script* set explicitly;
     * Chromium's network stack attaches Fetch Metadata (Sec-Fetch-*) and Client Hints (Sec-Ch-Ua-*)
     * headers itself, later in the pipeline, so they never show up here even though the real
     * browser request that this stream URL was captured from did carry them. Bot-management CDNs
     * (Cloudflare and similar) commonly gate on exactly those headers being present and consistent
     * with the User-Agent, which a plain HTTP client omits by default - reconstructing them here is
     * what makes a request "look" like the same browser load the WebView made, for any site using
     * this same BROWSER runtime, not one CDN's quirk.
     */
    private fun playbackHeaders(streamUrl: String, headers: Map<String, String>, pageUrl: String, cookie: String?): Map<String, String> = buildMap {
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank() && !name.equals("referer", true) && !name.equals("origin", true)) put(name, value)
        }
        put("User-Agent", CHROME_USER_AGENT)
        put("Referer", pageUrl)
        put("Origin", originOf(pageUrl))
        put("Accept", "*/*")
        put("Sec-Fetch-Dest", "video")
        put("Sec-Fetch-Mode", "no-cors")
        put("Sec-Fetch-Site", if (hostOf(streamUrl) == hostOf(pageUrl)) "same-origin" else "cross-site")
        put("Sec-Ch-Ua", "\"Not)A;Brand\";v=\"8\", \"Chromium\";v=\"137\", \"Android WebView\";v=\"137\"")
        put("Sec-Ch-Ua-Mobile", "?1")
        put("Sec-Ch-Ua-Platform", "\"Android\"")
        cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
    }

    private fun qualityFromUrl(url: String): String? = QUALITY.find(url)?.groupValues?.get(1)?.let { "${it}p" }

    private companion object {
        const val TIMEOUT_MS = 25_000L
        const val AUDIO_PROBE_TIMEOUT_MS = 2_500L
        const val PROBE_DELAY_MS = 500L
        const val MAX_PROBES = 24
        const val STREAM_SETTLE_DELAY_MS = 1_000L
        const val CHROME_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
        const val TAG = "BrowserPlayerResolver"
        val QUALITY = Regex("""(?<!\\d)(240|360|480|540|720|1080|1440|2160)(?:p|\\.m3u8|/)""")
        val AUDIO_MEDIA_URI = Regex("#EXT-X-MEDIA:[^\\n]*TYPE=AUDIO[^\\n]*URI=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        val VTT_URL = Regex("https?://.+\\.vtt(?:[?#].*)?", RegexOption.IGNORE_CASE)
        const val VIDEO_ELEMENT_PROBE = """;(function(){try{var v=document.querySelector('video');if(!v)return;var r=function(){var u=v.currentSrc||v.src||'';if(/\\.m3u8(?:[?#]|$)/i.test(u))HibikiResolver.stream(u)};r();v.addEventListener('loadedmetadata',r,{once:true});v.addEventListener('canplay',r,{once:true});v.addEventListener('playing',r,{once:true})}catch(e){}})();"""
    }
}

internal object BrowserResolverRouting {
    fun supports(link: PlayerLink, resolvers: List<BrowserScriptResolver>): Boolean =
        link.type == PlayerType.EMBED && resolvers.any { it.supportsBrowser(link) }
}

internal class CaptureSession(val webView: WebView, val handler: Handler)

private data class BrowserCaptureResult(
    val streams: List<BrowserCapturedStream>,
    val subtitles: List<BrowserCapturedSubtitle>,
    val session: CaptureSession?,
)

internal enum class BrowserCaptureOrigin { NETWORK, VIDEO_ELEMENT, SOURCE_MASTER, SOURCE_VIDEO, SOURCE_AUDIO }
internal data class BrowserCapturedStream(val url: String, val headers: Map<String, String>, val origin: BrowserCaptureOrigin)
private data class BrowserCapturedSubtitle(
    val url: String,
    val label: String?,
    val language: String?,
    val headers: Map<String, String>,
)

/** Pure selection policy, unit-testable without Android WebView or a real site. */
internal object BrowserStreamSelector {
    fun isHls(url: String): Boolean = HLS_URL.containsMatchIn(url)
    fun select(captures: List<BrowserCapturedStream>): BrowserCapturedStream? = rank(captures).firstOrNull()
    fun findAudio(captures: List<BrowserCapturedStream>): BrowserCapturedStream? = captures.asReversed().firstOrNull { it.origin == BrowserCaptureOrigin.SOURCE_AUDIO }
        ?: captures.asReversed().firstOrNull { isAudioRendition(it.url) }
    fun rank(captures: List<BrowserCapturedStream>): List<BrowserCapturedStream> = buildList {
        fun addDistinct(items: List<BrowserCapturedStream>) {
            items.forEach { candidate -> if (none { it.url == candidate.url }) add(candidate) }
        }
        val newestFirst = captures.asReversed()
        addDistinct(newestFirst.filter { it.origin == BrowserCaptureOrigin.SOURCE_MASTER })
        addDistinct(newestFirst.filter { it.origin == BrowserCaptureOrigin.SOURCE_VIDEO })
        addDistinct(newestFirst.filter { it.origin == BrowserCaptureOrigin.VIDEO_ELEMENT })
        addDistinct(newestFirst.filter { !isAudioRendition(it.url) })
        addDistinct(newestFirst)
    }
    private fun isAudioRendition(url: String): Boolean = Regex("""(?:^|[?/_-])(audio|aac|opus)(?:[?/_.-]|$)""", RegexOption.IGNORE_CASE).containsMatchIn(url)
    // Matches ".../foo.m3u8" (path or last query/fragment segment), not an unrelated query value
    // like a telemetry beacon's "?file=foo.m3u8" parameter tacked onto a non-HLS endpoint.
    private val HLS_URL = Regex("""\.m3u8(?:[?#]|$)""", RegexOption.IGNORE_CASE)
}
