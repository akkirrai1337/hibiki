package org.akkirrai.hibiki.core.source

import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoStream
import org.akkirrai.beakokit.http.hostOf
import org.akkirrai.beakokit.http.normalizeUrl
import org.akkirrai.beakokit.http.originOf
import org.akkirrai.hibiki.core.log.AppLogger
import kotlin.coroutines.resume

class AllohaWebViewExtractor(
    private val context: Context,
) : StreamExtractor {
    override fun supports(link: PlayerLink): Boolean {
        if (link.type != PlayerType.EMBED) return false
        val host = hostOf(link.url)?.lowercase().orEmpty()
        return host == "alloha.yani.tv" || host.endsWith(".alloha.yani.tv")
    }

    override suspend fun extract(link: PlayerLink): VideoStream =
        extractVariants(link).first()

    override suspend fun extractVariants(link: PlayerLink): List<VideoStream> {
        val result = withContext(Dispatchers.Main) {
            captureStreams(normalizeUrl(link.url))
        } ?: throw SourceException("Alloha не вернул поток")

        val qualityStreams = result.qualities.entries.map { (quality, captured) ->
            VideoStream(
                url = captured.url,
                type = StreamType.HLS,
                quality = quality.takeUnless { it.equals("auto", ignoreCase = true) },
                headers = captured.headers,
            )
        }

        val candidates = if (qualityStreams.isNotEmpty()) {
            qualityStreams
        } else {
            listOf(
                VideoStream(
                    url = result.fallback.url,
                    type = StreamType.HLS,
                    quality = null,
                    headers = result.fallback.headers,
                ),
            )
        }

        return candidates
            .distinctBy { stream -> stream.url to stream.quality }
            .sortedByDescending { qualityValue(it.quality) ?: 0 }
    }

    private suspend fun captureStreams(iframeUrl: String): CaptureResult? =
        suspendCancellableCoroutine { continuation ->
            var webView: WebView? = null
            var delivered = false
            val handler = Handler(Looper.getMainLooper())
            val captured = LinkedHashMap<String, CapturedStream>()
            var fallback: CapturedStream? = null
            var pendingQuality: String? = null
            var qualityProbeAttempts = 0
            var settleRunnable: Runnable? = null
            var probeRetryRunnable: Runnable? = null
            lateinit var timeoutRunnable: Runnable

            fun destroyView() {
                val current = webView
                webView = null
                handler.post { current?.destroy() }
            }

            fun deliver(result: CaptureResult?) {
                if (delivered) return
                delivered = true
                settleRunnable?.let(handler::removeCallbacks)
                probeRetryRunnable?.let(handler::removeCallbacks)
                handler.removeCallbacks(timeoutRunnable)
                destroyView()
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            fun snapshot(): CaptureResult? {
                val fallbackStream = fallback ?: return null
                val qualityMap = captured.toSortedQualityMap()
                val selectedFallback = qualityMap.values.lastOrNull()?.let { selected ->
                    captured.values.firstOrNull { stream -> stream.url == selected.url }
                } ?: fallbackStream
                return CaptureResult(
                    fallback = selectedFallback,
                    qualities = qualityMap.takeIf { qualities -> qualities.size > 1 } ?: LinkedHashMap(),
                )
            }

            fun scheduleDelivery(delayMs: Long = STREAM_SETTLE_DELAY_MS) {
                settleRunnable?.let(handler::removeCallbacks)
                val runnable = Runnable { deliver(snapshot()) }
                settleRunnable = runnable
                handler.postDelayed(runnable, delayMs)
            }

            fun capture(url: String, headers: Map<String, String>) {
                val stream = CapturedStream(
                    url = url,
                    headers = normalizePlaybackHeaders(
                        capturedHeaders = headers,
                        referer = iframeUrl,
                    ),
                )
                fallback = stream
                val quality = qualityLabelFromUrl(url)
                    ?: pendingQuality?.let(::normalizeQualityLabel)
                    ?: "auto"
                captured[quality] = stream
                scheduleDelivery()
            }

            val bridge = object {
                @JavascriptInterface
                fun quality(label: String) {
                    handler.post {
                        pendingQuality = normalizeQualityLabel(label)
                    }
                }

                @JavascriptInterface
                fun done() {
                    handler.post {
                        scheduleDelivery()
                    }
                }
            }

            fun runQualityProbe(view: WebView) {
                if (delivered || webView !== view || qualityProbeAttempts >= MAX_QUALITY_PROBE_ATTEMPTS) return
                qualityProbeAttempts += 1
                view.evaluateJavascript(qualityProbeScript()) { result ->
                    if (!delivered && webView === view && result.contains("no-player") && qualityProbeAttempts < MAX_QUALITY_PROBE_ATTEMPTS) {
                        val retry = Runnable {
                            probeRetryRunnable = null
                            runQualityProbe(view)
                        }
                        probeRetryRunnable = retry
                        handler.postDelayed(retry, QUALITY_PROBE_RETRY_DELAY_MS)
                    }
                }
            }

            timeoutRunnable = Runnable {
                AppLogger.w(TAG, "Alloha timed out without captured stream")
                deliver(snapshot())
            }
            handler.postDelayed(timeoutRunnable, TIMEOUT_MS)

            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                @Suppress("DEPRECATION")
                settings.allowFileAccess = false
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = CHROME_USER_AGENT

                addJavascriptInterface(bridge, "AllohaBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = request.url.toString()
                        if (isStreamUrl(url)) {
                            handler.post {
                                capture(url = url, headers = request.requestHeaders)
                            }
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        if (qualityProbeAttempts == 0) {
                            runQualityProbe(view)
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        AppLogger.w(
                            TAG,
                            "Alloha WebView error ${error.errorCode} on ${request.url}: ${error.description}",
                        )
                    }

                    override fun onReceivedSslError(
                        view: WebView,
                        handler: SslErrorHandler,
                        error: SslError,
                    ) {
                        AppLogger.w(
                            TAG,
                            "Alloha WebView SSL error ${error.primaryError} on ${error.url}",
                        )
                        super.onReceivedSslError(view, handler, error)
                    }
                }

                loadDataWithBaseURL(
                    "https://alloha.yani.tv/",
                    wrapperHtml(iframeUrl),
                    "text/html",
                    "utf-8",
                    null,
                )
            }

            continuation.invokeOnCancellation {
                settleRunnable?.let(handler::removeCallbacks)
                probeRetryRunnable?.let(handler::removeCallbacks)
                handler.removeCallbacks(timeoutRunnable)
                destroyView()
            }
        }

    private fun isStreamUrl(url: String): Boolean {
        val normalized = url.lowercase()
        return normalized.contains(".m3u8") &&
            !normalized.contains("doubleclick") &&
            !normalized.contains("/ads") &&
            !normalized.contains("ima")
    }

    private fun wrapperHtml(iframeUrl: String): String {
        val escapedUrl = iframeUrl
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    * { margin: 0; padding: 0; }
                    html, body, iframe { width: 100%; height: 100%; border: 0; background: #000; }
                </style>
            </head>
            <body>
                <iframe src="$escapedUrl" allow="autoplay; fullscreen" allowfullscreen></iframe>
            </body>
            </html>
        """.trimIndent()
    }

    private fun qualityValue(label: String?): Int? =
        label?.let { QUALITY_NUMBER.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

    private fun qualityLabelFromUrl(url: String): String? =
        QUALITY_IN_URL.find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf(KNOWN_QUALITIES::contains)
            ?.let { "${it}p" }

    private fun normalizeQualityLabel(label: String): String? {
        val cleaned = label
            .replace(HTML_TAGS, "")
            .trim()
            .takeIf { it.isNotEmpty() && !it.startsWith("<<<") && it != "[object Object]" }
            ?: return null

        return qualityValue(cleaned)
            ?.takeIf(KNOWN_QUALITIES::contains)
            ?.let { "${it}p" }
            ?: cleaned
    }

    private fun LinkedHashMap<String, CapturedStream>.toSortedQualityMap(): LinkedHashMap<String, CapturedStream> {
        return entries
            .sortedWith { left, right ->
                val leftQuality = qualityValue(left.key)
                val rightQuality = qualityValue(right.key)
                when {
                    leftQuality != null && rightQuality != null -> leftQuality.compareTo(rightQuality)
                    leftQuality == null && rightQuality != null -> -1
                    leftQuality != null && rightQuality == null -> 1
                    else -> 0
                }
            }
            .associateTo(LinkedHashMap()) { it.toPair() }
    }

    private fun normalizePlaybackHeaders(
        capturedHeaders: Map<String, String>,
        referer: String,
    ): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        capturedHeaders.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                headers[name] = value
            }
        }
        headers.removeByName("User-Agent")
        headers.removeByName("Referer")
        headers.removeByName("Referrer")
        headers.removeByName("Origin")
        headers.removeByName("Accept")
        headers["User-Agent"] = CHROME_USER_AGENT
        headers["Referer"] = referer
        headers["Origin"] = originOf(referer)
        headers["Accept"] = "*/*"
        return headers
    }

    private fun qualityProbeScript(): String = """
        (function(){
            function isPlayer(value) {
                return value && (
                    typeof value.api === "function" ||
                    (value.media && typeof value.media.querySelectorAll === "function" && "quality" in value)
                );
            }
            function labelOf(item) {
                if (typeof item === "string") return item;
                if (typeof item === "number") return String(item);
                if (item && typeof item === "object") {
                    return item.title || item.label || item.name || item.quality || item.text || item.value || item.size || "";
                }
                return String(item || "");
            }
            function findPlayer(win) {
                var names = ["player", "Player", "playerjs", "pl"];
                for (var i = 0; i < names.length; i++) {
                    try {
                        var candidate = win[names[i]];
                        if (isPlayer(candidate)) return candidate;
                    } catch (e) {}
                }
                try {
                    var elementPlayer = win.document && win.document.getElementById("player");
                    if (isPlayer(elementPlayer)) return elementPlayer;
                } catch (e) {}
                for (var key in win) {
                    try {
                        if (isPlayer(win[key])) return win[key];
                    } catch (e) {}
                }
                return null;
            }
            function addLabels(target, raw) {
                if (!raw) return;
                if (typeof raw === "string") {
                    raw.split(",").forEach(function(item){ addLabels(target, item); });
                    return;
                }
                if (Array.isArray(raw)) {
                    raw.forEach(function(item){ addLabels(target, item); });
                    return;
                }
                if (typeof raw.length === "number" && typeof raw !== "function") {
                    for (var i = 0; i < raw.length; i++) addLabels(target, raw[i]);
                    return;
                }
                target.push(labelOf(raw));
            }
            function normalize(labels) {
                var known = {240:true,360:true,480:true,540:true,720:true,1080:true,1440:true,2160:true};
                var result = {};
                labels.forEach(function(label){
                    var text = String(label || "").replace(/<[^>]+>/g, "").trim();
                    var match = text.match(/\d{3,4}/);
                    if (!match) return;
                    var quality = parseInt(match[0], 10);
                    if (known[quality]) result[quality] = String(quality);
                });
                return Object.keys(result)
                    .map(function(value){ return parseInt(value, 10); })
                    .sort(function(a, b){ return a - b; })
                    .map(function(value){ return String(value); });
            }
            function collectDomLabels(win, player) {
                var labels = [];
                var roots = [];
                if (player && player.media) roots.push(player.media);
                if (win.document) roots.push(win.document);
                for (var rootIndex = 0; rootIndex < roots.length; rootIndex++) {
                    var root = roots[rootIndex];
                    try {
                        var sourceNodes = root.querySelectorAll("source[size][src]");
                        for (var i = 0; i < sourceNodes.length; i++) {
                            labels.push(sourceNodes[i].getAttribute("size"));
                        }
                    } catch (e) {}
                    try {
                        var qualityNodes = root.querySelectorAll(
                            "[data-allplay='quality'], [name='quality'], [role='menuitemradio'], [data-quality], [quality]"
                        );
                        for (var j = 0; j < qualityNodes.length; j++) {
                            labels.push(
                                qualityNodes[j].getAttribute("value") ||
                                qualityNodes[j].getAttribute("data-quality") ||
                                qualityNodes[j].getAttribute("quality") ||
                                qualityNodes[j].getAttribute("aria-label") ||
                                qualityNodes[j].textContent
                            );
                        }
                    } catch (e) {}
                }
                return normalize(labels);
            }
            function collectPlayerLabels(player) {
                var labels = [];
                try { addLabels(labels, player.config && player.config.quality && player.config.quality.options); } catch (e) {}
                try { addLabels(labels, player.options && player.options.quality); } catch (e) {}
                try { addLabels(labels, player.config && player.config.hlsSource); } catch (e) {}
                try { addLabels(labels, player.config && player.config.sources); } catch (e) {}
                try { addLabels(labels, player.sources); } catch (e) {}
                return normalize(labels);
            }
            function collectApiLabels(player) {
                var labels = [];
                try {
                    if (player && typeof player.api === "function") {
                        addLabels(labels, player.api("qualities"));
                    }
                } catch (e) {}
                try {
                    if (player && player.media && typeof player.media.querySelectorAll === "function") {
                        addLabels(labels, collectDomLabels(window, player));
                    }
                } catch (e) {}
                return normalize(labels);
            }
            function playCurrent(win, player) {
                try { player.api("play"); } catch (e) {}
                try {
                    var video = win.document && win.document.querySelector("video");
                    if (video) video.play().catch(function(){});
                } catch (e) {}
            }
            function switchByVideoSource(win, quality) {
                try {
                    var source = win.document && win.document.querySelector("source[size='" + quality + "'][src]");
                    var video = win.document && win.document.querySelector("video");
                    if (!source || !video) return false;
                    video.src = source.getAttribute("src");
                    video.load();
                    video.play().catch(function(){});
                    return true;
                } catch (e) {
                    return false;
                }
            }

            var frame = document.querySelector("iframe");
            var win = frame && frame.contentWindow ? frame.contentWindow : window;
            var player = findPlayer(win);
            if (!player) return "no-player";

            var qualities = collectDomLabels(win, player);
            if (!qualities.length) qualities = collectPlayerLabels(player);
            if (!qualities.length) qualities = collectApiLabels(player);
            if (!qualities.length) {
                playCurrent(win, player);
                AllohaBridge.done();
                return JSON.stringify([]);
            }

            var index = 0;
            function switchNext() {
                if (index >= qualities.length) {
                    AllohaBridge.done();
                    return;
                }
                var quality = qualities[index++];
                AllohaBridge.quality(quality);
                try {
                    var numericQuality = parseInt(quality, 10);
                    if (numericQuality && typeof player.quality !== "undefined") {
                        player.quality = numericQuality;
                    } else if (typeof player.api === "function") {
                        try {
                            player.api("quality", numericQuality || quality);
                        } catch (e) {
                            player.api("quality", index - 1);
                        }
                    } else if (numericQuality) {
                        switchByVideoSource(win, numericQuality);
                    }
                    try {
                        frame.contentWindow.postMessage(JSON.stringify({api:"quality", value:numericQuality}), "*");
                        frame.contentWindow.postMessage(JSON.stringify({api:"quality", value:index - 1}), "*");
                    } catch (e) {}
                } catch (e) {}
                playCurrent(win, player);
                setTimeout(switchNext, $QUALITY_SWITCH_DELAY_MS);
            }
            switchNext();
        })();
    """.trimIndent()

    private data class CaptureResult(
        val fallback: CapturedStream,
        val qualities: LinkedHashMap<String, CapturedStream>,
    )

    private data class CapturedStream(
        val url: String,
        val headers: Map<String, String>,
    )

    private fun MutableMap<String, String>.removeByName(name: String) {
        keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(::remove)
    }

    private companion object {
        const val TAG = "AllohaWebViewExtractor"
        const val TIMEOUT_MS = 25_000L
        const val STREAM_SETTLE_DELAY_MS = 2_500L
        const val QUALITY_SWITCH_DELAY_MS = 1_500L
        const val QUALITY_PROBE_RETRY_DELAY_MS = 500L
        const val MAX_QUALITY_PROBE_ATTEMPTS = 8
        const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

        val KNOWN_QUALITIES = setOf(240, 360, 480, 540, 720, 1080, 1440, 2160)
        val QUALITY_NUMBER = Regex("""(\d{3,4})""")
        val QUALITY_IN_URL = Regex("""(?<!\d)(\d{3,4})(?:p|\.mp4|/)""")
        val HTML_TAGS = Regex("""<[^>]+>""")
    }
}
