package org.akkirrai.hibiki.core.source

import android.os.Handler
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.http.hostOf
import org.akkirrai.beakokit.http.resolveUrl
import org.akkirrai.hibiki.core.log.AppLogger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Some CDNs behind Cloudflare-style bot management can tell a plain HTTP client (OkHttp, media3's
 * HttpURLConnection-based data source) apart from a real Chromium TLS handshake regardless of what
 * headers it presents - the fingerprint is set at the TLS layer, before any HTTP header is read.
 * A [BrowserPlayerWebViewExtractor] already runs a real WebView to resolve the stream URL; this
 * relay reuses that same WebView's `fetch()` (a genuine Chromium network request) to actually pull
 * the HLS playlists and segments too, and republishes the bytes over a 127.0.0.1 loopback HTTP
 * server that ExoPlayer connects to instead of the CDN directly. This is a fallback path only
 * (see [BrowserPlayerWebViewExtractor.extractVariants]): a direct URL is tried first and this is
 * only reached when that direct fetch was blocked, so sites without this problem never pay for it.
 */
internal object WebViewStreamRelay {
    private const val TAG = "WebViewStreamRelay"
    private const val SESSION_IDLE_TIMEOUT_MS = 3 * 60_000L
    private const val FETCH_TIMEOUT_SECONDS = 20L
    private const val JS_FETCH_TIMEOUT_MS = 12_000

    private class Session(val webView: WebView, val handler: Handler, val headers: Map<String, String>) {
        val lastUsed = AtomicLong(System.currentTimeMillis())
    }

    private data class FetchResult(val status: Int, val contentType: String, val contentRange: String, val body: ByteArray)

    private val sessions = ConcurrentHashMap<String, Session>()
    private val pending = ConcurrentHashMap<String, CompletableFuture<FetchResult>>()
    private val executor = Executors.newCachedThreadPool()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var port = -1
    @Volatile private var serverSocket: ServerSocket? = null

    /** Injects the relay's JS bridge into [webView]. A `WebView.addJavascriptInterface` call only
     * becomes visible to a page's own JS *after that page's next load* - calling this once up front
     * (before the first `loadUrl`, alongside any other interfaces the caller installs) means the
     * bridge is already present by the time [register] is used later on the same already-loaded
     * page, instead of the page silently seeing a missing/undefined global. Must run on the main
     * thread, same as any other WebView call. */
    fun installBridge(webView: WebView) {
        webView.addJavascriptInterface(Bridge, BRIDGE_NAME)
    }

    /** Registers [webView] (already navigated, already carrying the right session/cookies, and
     * already carrying the bridge via [installBridge]) as the fetch backend for a new relay session
     * and returns its token. Safe to call repeatedly; each call creates an independent session so
     * concurrent playback attempts don't share a WebView. Must be called off the main thread - it
     * binds the loopback [ServerSocket] the first time, which is itself a blocking network call. */
    fun register(webView: WebView, handler: Handler, headers: Map<String, String>): String {
        ensureServerStarted()
        val token = UUID.randomUUID().toString()
        sessions[token] = Session(webView, handler, headers)
        reapIdleSessions()
        AppLogger.d(TAG, "Session registered: token=${token.take(8)}, port=$port, sessions=${sessions.size}")
        return token
    }

    /** Drops a session immediately without waiting for the idle reaper, e.g. when the caller knows
     * upfront that no relay fallback will ever be needed for it. */
    fun discard(webView: WebView, handler: Handler) {
        handler.post { webView.destroy() }
    }

    fun proxyUrl(token: String, targetUrl: String): String =
        "http://127.0.0.1:$port/relay/$token?u=${URLEncoder.encode(targetUrl, "UTF-8")}"

    @Synchronized
    private fun ensureServerStarted() {
        if (serverSocket != null) return
        try {
            // InetAddress.getLoopbackAddress() can resolve to the IPv6 loopback (::1) on a
            // dual-stack device, while proxyUrl() below and the app's HTTP clients address the
            // relay by the IPv4 literal "127.0.0.1" - binding by name pins it to IPv4 so the two
            // always agree, regardless of the device's loopback resolution order.
            val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = socket
            port = socket.localPort
            AppLogger.d(TAG, "Relay server bound: port=$port")
            executor.execute { acceptLoop(socket) }
            executor.execute { reaperLoop() }
        } catch (error: Exception) {
            AppLogger.w(TAG, "Relay server failed to bind", error)
            throw error
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        AppLogger.d(TAG, "Accept loop running: port=${socket.localPort}")
        while (!socket.isClosed) {
            val connection = try {
                socket.accept()
            } catch (error: Exception) {
                if (socket.isClosed) {
                    AppLogger.w(TAG, "Accept loop stopped: socket closed")
                    return
                }
                AppLogger.w(TAG, "Accept failed", error)
                continue
            }
            AppLogger.d(TAG, "Accepted connection")
            executor.execute { handleConnection(connection) }
        }
    }

    private fun reaperLoop() {
        while (true) {
            Thread.sleep(30_000)
            reapIdleSessions()
        }
    }

    private fun reapIdleSessions() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { (_, session) ->
            val idle = now - session.lastUsed.get() > SESSION_IDLE_TIMEOUT_MS
            if (idle) session.handler.post { session.webView.destroy() }
            idle
        }
    }

    private fun handleConnection(socket: Socket) {
        socket.use { conn ->
            try {
                val reader = BufferedReader(InputStreamReader(conn.getInputStream(), Charsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                val path = requestLine.split(" ").getOrNull(1) ?: return
                var range: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val (name, value) = line.split(":", limit = 2).let { it[0].trim() to it.getOrElse(1) { "" }.trim() }
                    if (name.equals("Range", ignoreCase = true)) range = value
                }
                respond(conn.getOutputStream(), path, range)
            } catch (error: Exception) {
                AppLogger.w(TAG, "Relay connection failed", error)
            }
        }
    }

    private fun respond(out: OutputStream, path: String, range: String?) {
        val (token, target) = parsePath(path) ?: return writeStatus(out, 400, "Bad Request")
        val session = sessions[token] ?: return writeStatus(out, 502, "No active browser session")
        session.lastUsed.set(System.currentTimeMillis())

        val result = try {
            fetchViaWebView(session, target, range)
        } catch (error: Exception) {
            AppLogger.w(TAG, "Relay fetch failed: host=${hostOf(target)}", error)
            return writeStatus(out, 502, "Upstream fetch failed")
        }

        val isPlaylist = result.contentType.contains("mpegurl", ignoreCase = true) ||
            result.body.decodeToString(endIndex = minOf(result.body.size, 16)).trimStart().startsWith("#EXTM3U")
        if (isPlaylist) {
            val rewritten = rewritePlaylist(result.body.decodeToString(), target, token)
            val bytes = rewritten.encodeToByteArray()
            writeHeaders(out, 200, "application/vnd.apple.mpegurl", bytes.size, null)
            out.write(bytes)
        } else {
            writeHeaders(out, result.status, result.contentType.ifBlank { "application/octet-stream" }, result.body.size, result.contentRange.takeIf(String::isNotBlank))
            out.write(result.body)
        }
        out.flush()
    }

    private fun parsePath(path: String): Pair<String, String>? {
        val withoutPrefix = path.removePrefix("/relay/")
        if (withoutPrefix == path) return null
        val token = withoutPrefix.substringBefore('?')
        val query = withoutPrefix.substringAfter('?', "")
        val target = query.split("&")
            .firstOrNull { it.startsWith("u=") }
            ?.substringAfter("u=")
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?: return null
        return token to target
    }

    private fun rewritePlaylist(playlist: String, baseUrl: String, token: String): String =
        playlist.lineSequence().joinToString("\n") { rawLine ->
            val line = rawLine.trimEnd('\r')
            when {
                line.startsWith("#") -> URI_ATTR.replace(line) { match ->
                    "URI=\"${proxyUrl(token, resolveUrl(baseUrl, match.groupValues[1]))}\""
                }
                line.isBlank() -> line
                else -> proxyUrl(token, resolveUrl(baseUrl, line))
            }
        }

    private fun fetchViaWebView(session: Session, url: String, range: String?): FetchResult {
        val reqId = UUID.randomUUID().toString()
        val future = CompletableFuture<FetchResult>()
        pending[reqId] = future
        try {
            val script = buildFetchScript(reqId, url, session.headers, range)
            AppLogger.d(TAG, "Dispatching WebView fetch: reqId=${reqId.take(8)}, host=${hostOf(url)}")
            session.handler.post {
                session.webView.evaluateJavascript(script) { result ->
                    AppLogger.d(TAG, "WebView fetch script started: reqId=${reqId.take(8)}, result=$result")
                }
            }
            return future.get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            pending.remove(reqId)
        }
    }

    private fun buildFetchScript(reqId: String, url: String, headers: Map<String, String>, range: String?): String {
        val headerEntries = if (range != null) headers + ("Range" to range) else headers
        // The page's own player (still running live in this same WebView - capture() never stops
        // it) can be fetching this exact URL on its own at the same moment, and Chromium can then
        // hand both callers a body-already-disturbed Response for what looks like one coalesced
        // request. A harmless cache-busting param (most signed-URL CDNs validate only their own
        // known params, not reject unknown extras) makes this a genuinely distinct request/response.
        val bustedUrl = url + (if ("?" in url) "&" else "?") + "_hibikiRelay=" + System.nanoTime()
        val urlJson = json.encodeToString(bustedUrl)
        val headersJson = json.encodeToString(headerEntries)
        val reqIdJson = json.encodeToString(reqId)
        // A hung fetch() (the CDN accepting the TCP connection but never responding to an XHR-style
        // request, as opposed to a <video>-initiated one) would otherwise burn the full Kotlin-side
        // timeout before this call is even known to be stuck - aborting client-side first gives a
        // fast, unambiguous "no response" signal instead of a silent multi-second stall.
        // XMLHttpRequest instead of fetch(): a fetch() Response's body is a ReadableStream, and if
        // this page's embed has a Service Worker (registered for its own origin/scope, controlling
        // every fetch() call made by any script running in its document - ours included), whatever
        // that worker does with the stream while deciding how to respond can leave our caller a
        // body already marked read, surfacing as "body stream already read"/"already used". XHR's
        // response is a plain buffer, never a stream, so that failure mode can't happen with it -
        // and unlike a workaround fetch grabbed from a throwaway iframe, XHR still runs as this same
        // top document, so the browser still computes the real page's Referer automatically instead
        // of an opaque iframe one, which is what this CDN's hotlink check actually needs to see.
        return """
            (function(){
              try {
                var xhr = new XMLHttpRequest();
                xhr.open('GET', $urlJson, true);
                xhr.responseType = 'arraybuffer';
                var headers = $headersJson;
                Object.keys(headers).forEach(function(name){
                  try { xhr.setRequestHeader(name, headers[name]); } catch (e) {}
                });
                xhr.onload = function(){
                  try {
                    var bytes = new Uint8Array(xhr.response || new ArrayBuffer(0));
                    var chunks = [];
                    for (var i = 0; i < bytes.length; i += 8192) {
                      chunks.push(String.fromCharCode.apply(null, bytes.subarray(i, i + 8192)));
                    }
                    $BRIDGE_NAME.onResult($reqIdJson, xhr.status, xhr.getResponseHeader('content-type') || '', xhr.getResponseHeader('content-range') || '', btoa(chunks.join('')));
                  } catch (e) { $BRIDGE_NAME.onError($reqIdJson, 'onload: ' + String(e)); }
                };
                xhr.onerror = function(){ $BRIDGE_NAME.onError($reqIdJson, 'network error'); };
                xhr.ontimeout = function(){ $BRIDGE_NAME.onError($reqIdJson, 'timeout'); };
                xhr.timeout = $JS_FETCH_TIMEOUT_MS;
                xhr.send();
              } catch(e) { $BRIDGE_NAME.onError($reqIdJson, 'sync: ' + String(e)); }
            })();
        """.trimIndent()
    }

    private fun writeStatus(out: OutputStream, status: Int, message: String) {
        val bytes = message.encodeToByteArray()
        writeHeaders(out, status, "text/plain", bytes.size, null)
        out.write(bytes)
        out.flush()
    }

    private fun writeHeaders(out: OutputStream, status: Int, contentType: String, length: Int, contentRange: String?) {
        val reason = if (status == 206) "Partial Content" else if (status in 200..299) "OK" else "Error"
        val headers = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: $length\r\n")
            append("Accept-Ranges: bytes\r\n")
            contentRange?.let { append("Content-Range: $it\r\n") }
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(headers.toByteArray(Charsets.ISO_8859_1))
    }

    private const val BRIDGE_NAME = "HibikiRelayBridge"
    private val URI_ATTR = Regex("URI=\"([^\"]+)\"")

    private object Bridge {
        @JavascriptInterface
        fun onResult(reqId: String, status: Int, contentType: String, contentRange: String, base64Body: String) {
            pending.remove(reqId)?.complete(
                FetchResult(status, contentType, contentRange, android.util.Base64.decode(base64Body, android.util.Base64.DEFAULT)),
            )
        }

        @JavascriptInterface
        fun onError(reqId: String, message: String) {
            AppLogger.w(TAG, "WebView fetch rejected: $message")
            pending.remove(reqId)?.completeExceptionally(RuntimeException(message))
        }
    }
}
