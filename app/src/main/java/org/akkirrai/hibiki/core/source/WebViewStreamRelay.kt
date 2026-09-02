package org.akkirrai.hibiki.core.source

import android.content.Context
import android.os.Handler
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.http.hostOf
import org.akkirrai.beakokit.http.isAbsoluteUrl
import org.akkirrai.beakokit.http.originOf
import org.akkirrai.beakokit.http.resolveUrl
import org.akkirrai.hibiki.core.log.AppLogger
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import com.google.android.gms.net.CronetProviderInstaller
import com.google.android.gms.tasks.Tasks
import org.chromium.net.CronetEngine

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

    private class Session(
        val webView: WebView,
        val handler: Handler,
        private val headers: Map<String, String>,
        initialUrls: Collection<String>,
        resourceHeaders: Map<String, Map<String, String>>,
        val streaming: Boolean,
    ) {
        val appContext: Context = webView.context.applicationContext
        val lastUsed = AtomicLong(System.currentTimeMillis())
        private val allowedOrigins = ConcurrentHashMap.newKeySet<String>().apply {
            initialUrls.mapNotNull(::safeOrigin).forEach(::add)
        }
        private val headersByUrl = ConcurrentHashMap<String, Map<String, String>>().apply {
            resourceHeaders.forEach { (url, value) ->
                if (safeOrigin(url) != null) put(url, value)
            }
        }

        fun allows(url: String): Boolean = safeOrigin(url)?.let(allowedOrigins::contains) == true

        fun headersFor(url: String): Map<String, String> = headersByUrl[url] ?: headers

        fun authorize(url: String, inheritedHeaders: Map<String, String>): Boolean = safeOrigin(url)?.let { origin ->
            allowedOrigins += origin
            headersByUrl.putIfAbsent(url, inheritedHeaders)
            true
        } == true
    }

    private data class FetchResult(
        val status: Int,
        val contentType: String,
        val contentRange: String,
        val finalUrl: String,
        val body: ByteArray,
    )

    private data class StreamMetadata(
        val status: Int,
        val contentType: String,
        val contentRange: String,
        val contentLength: Long?,
        val finalUrl: String,
    )

    private sealed interface StreamEvent {
        data class Data(val bytes: ByteArray) : StreamEvent
        data object Complete : StreamEvent
        data class Failure(val message: String) : StreamEvent
    }

    private class StreamTransfer(private val onActivity: () -> Unit) {
        val metadata = CompletableFuture<StreamMetadata>()
        // JavascriptInterface calls are synchronous from Chromium's bridge thread. A bounded queue
        // therefore provides real backpressure: a fast CDN cannot base64-push an entire MP4 into
        // native memory while ExoPlayer is consuming the loopback socket more slowly.
        val events = ArrayBlockingQueue<StreamEvent>(STREAM_QUEUE_CAPACITY)

        fun touch() = onActivity()
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val pending = ConcurrentHashMap<String, CompletableFuture<FetchResult>>()
    private val pendingStreams = ConcurrentHashMap<String, StreamTransfer>()
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
    fun register(
        webView: WebView,
        handler: Handler,
        headers: Map<String, String>,
        initialUrls: Collection<String>,
        resourceHeaders: Map<String, Map<String, String>> = emptyMap(),
        streaming: Boolean = false,
    ): String {
        require(initialUrls.any { safeOrigin(it) != null }) { "Relay requires at least one HTTP(S) origin" }
        ensureServerStarted()
        val token = UUID.randomUUID().toString()
        sessions[token] = Session(webView, handler, headers, initialUrls, resourceHeaders, streaming)
        reapIdleSessions()
        AppLogger.d(TAG, "Session registered: token=${token.take(8)}, port=$port, sessions=${sessions.size}")
        return token
    }

    /** Drops a session immediately without waiting for the idle reaper, e.g. when the caller knows
     * upfront that no relay fallback will ever be needed for it. */
    fun discard(webView: WebView, handler: Handler) {
        handler.post { webView.destroyAndClearData() }
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
            if (idle) session.handler.post { session.webView.destroyAndClearData() }
            idle
        }
    }

    private fun handleConnection(socket: Socket) {
        socket.use { conn ->
            try {
                val reader = BufferedReader(InputStreamReader(conn.getInputStream(), Charsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                val requestParts = requestLine.split(" ")
                val method = requestParts.getOrNull(0).orEmpty()
                val path = requestParts.getOrNull(1) ?: return
                if (method != "GET" && method != "HEAD") {
                    return writeStatus(conn.getOutputStream(), 405, "Method Not Allowed")
                }
                var range: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val (name, value) = line.split(":", limit = 2).let { it[0].trim() to it.getOrElse(1) { "" }.trim() }
                    if (name.equals("Range", ignoreCase = true)) range = value
                }
                respond(conn.getOutputStream(), path, range, headOnly = method == "HEAD")
            } catch (error: Exception) {
                AppLogger.w(TAG, "Relay connection failed", error)
            }
        }
    }

    private fun respond(out: OutputStream, path: String, range: String?, headOnly: Boolean) {
        val (token, target) = parsePath(path) ?: return writeStatus(out, 400, "Bad Request")
        val session = sessions[token] ?: return writeStatus(out, 502, "No active browser session")
        if (!session.allows(target)) return writeStatus(out, 403, "Relay origin is not authorized")
        session.lastUsed.set(System.currentTimeMillis())

        if (session.streaming) {
            return respondStreaming(out, session, token, target, range, headOnly)
        }

        val result = try {
            fetchViaWebView(session, target, range)
        } catch (error: Exception) {
            AppLogger.w(TAG, "Relay fetch failed: host=${hostOf(target)}", error)
            return writeStatus(out, 502, "Upstream fetch failed")
        }

        val isPlaylist = result.contentType.contains("mpegurl", ignoreCase = true) ||
            result.body.decodeToString(endIndex = minOf(result.body.size, 16)).trimStart().startsWith("#EXTM3U")
        if (isPlaylist) {
            val rewritten = rewritePlaylist(result.body.decodeToString(), result.finalUrl.ifBlank { target }) { resolved ->
                check(session.authorize(resolved, session.headersFor(target))) { "Playlist contained a non-HTTP URL" }
                proxyUrl(token, resolved)
            }
            val bytes = rewritten.encodeToByteArray()
            writeHeaders(out, 200, "application/vnd.apple.mpegurl", bytes.size, null)
            if (!headOnly) out.write(bytes)
        } else {
            writeHeaders(out, result.status, result.contentType.ifBlank { "application/octet-stream" }, result.body.size, result.contentRange.takeIf(String::isNotBlank))
            if (!headOnly) out.write(result.body)
        }
        out.flush()
    }

    private fun respondStreaming(
        out: OutputStream,
        session: Session,
        token: String,
        target: String,
        range: String?,
        headOnly: Boolean,
    ) {
        val effectiveRange = range ?: if (headOnly) "bytes=0-0" else null
        val (reqId, transfer) = try {
            fetchStreamingViaWebView(session, target, effectiveRange)
        } catch (error: Exception) {
            AppLogger.w(TAG, "Streaming relay dispatch failed: host=${hostOf(target)}", error)
            return writeStatus(out, 502, "Upstream fetch failed")
        }
        val metadata = try {
            transfer.metadata.get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (error: Exception) {
            // A page can request a cross-origin video in `no-cors` mode, but JavaScript cannot
            // read that response body. This is a browser security boundary rather than an
            // upstream playback failure. Re-fetch it through Cronet, Chromium's native transport,
            // which preserves the browser-like TLS stack without being subject to page CORS.
            pendingStreams.remove(reqId)
            transfer.events.clear()
            AppLogger.w(TAG, "WebView stream body unavailable; retrying through Cronet: host=${hostOf(target)}", error)
            return respondStreamingViaCronet(out, session, token, target, effectiveRange, headOnly)
        }
        try {
            val isPlaylist = metadata.contentType.contains("mpegurl", ignoreCase = true) ||
                target.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
            if (isPlaylist) {
                val body = collectStream(transfer, MAX_PLAYLIST_BYTES)
                val rewritten = rewritePlaylist(body.decodeToString(), metadata.finalUrl.ifBlank { target }) { resolved ->
                    check(session.authorize(resolved, session.headersFor(target))) { "Playlist contained a non-HTTP URL" }
                    proxyUrl(token, resolved)
                }
                val bytes = rewritten.encodeToByteArray()
                writeHeaders(out, 200, "application/vnd.apple.mpegurl", bytes.size, null)
                if (!headOnly) out.write(bytes)
                out.flush()
                return
            }

            if (headOnly) {
                collectStream(transfer)
                writeStreamingHeaders(
                    out = out,
                    status = metadata.status,
                    contentType = metadata.contentType,
                    contentLength = metadata.contentLength,
                    contentRange = metadata.contentRange.takeIf(String::isNotBlank),
                    chunked = false,
                )
                out.flush()
                return
            }

            val chunked = metadata.contentLength == null
            writeStreamingHeaders(
                out = out,
                status = metadata.status,
                contentType = metadata.contentType,
                contentLength = metadata.contentLength,
                contentRange = metadata.contentRange.takeIf(String::isNotBlank),
                chunked = chunked,
            )
            consumeStream(transfer) { bytes ->
                if (chunked) {
                    out.write(bytes.size.toString(16).toByteArray(Charsets.ISO_8859_1))
                    out.write("\r\n".toByteArray(Charsets.ISO_8859_1))
                    out.write(bytes)
                    out.write("\r\n".toByteArray(Charsets.ISO_8859_1))
                } else {
                    out.write(bytes)
                }
                out.flush()
            }
            if (chunked) out.write("0\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            out.flush()
        } catch (error: Exception) {
            AppLogger.w(TAG, "Streaming relay failed: host=${hostOf(target)}", error)
        } finally {
            pendingStreams.remove(reqId)
            transfer.events.clear()
            transfer.events.offer(StreamEvent.Complete)
        }
    }

    private fun respondStreamingViaCronet(
        out: OutputStream,
        session: Session,
        token: String,
        target: String,
        range: String?,
        headOnly: Boolean,
    ) {
        val connection = try {
            ChromiumStreamTransport.open(session.appContext, target, session.headersFor(target), range)
        } catch (error: Exception) {
            AppLogger.w(TAG, "Cronet relay setup failed: host=${hostOf(target)}", error)
            return writeStatus(out, 502, "Chromium transport is unavailable")
        }
        try {
            val status = connection.responseCode
            val contentType = connection.contentType.orEmpty()
            val contentRange = connection.getHeaderField("Content-Range").orEmpty()
            val contentLength = connection.getHeaderFieldLong("Content-Length", -1).takeIf { it >= 0 }
            val finalUrl = connection.url.toString()
            if (status !in 200..299) {
                AppLogger.w(TAG, "Cronet relay rejected: status=$status, host=${hostOf(target)}")
                return writeStatus(out, status, "Upstream request was rejected")
            }
            val input = connection.inputStream.buffered()
            input.use {
                val isPlaylist = contentType.contains("mpegurl", ignoreCase = true) ||
                    target.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
                if (isPlaylist) {
                    val bytes = input.readBounded(MAX_PLAYLIST_BYTES)
                    val rewritten = rewritePlaylist(bytes.decodeToString(), finalUrl.ifBlank { target }) { resolved ->
                        check(session.authorize(resolved, session.headersFor(target))) { "Playlist contained a non-HTTP URL" }
                        proxyUrl(token, resolved)
                    }.encodeToByteArray()
                    writeHeaders(out, 200, "application/vnd.apple.mpegurl", rewritten.size, null)
                    if (!headOnly) out.write(rewritten)
                    out.flush()
                    return
                }
                if (headOnly) {
                    writeStreamingHeaders(out, status, contentType, contentLength, contentRange.ifBlank { null }, chunked = false)
                    out.flush()
                    return
                }
                val chunked = contentLength == null
                writeStreamingHeaders(out, status, contentType, contentLength, contentRange.ifBlank { null }, chunked)
                val buffer = ByteArray(STREAM_COPY_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    session.lastUsed.set(System.currentTimeMillis())
                    if (chunked) {
                        out.write(count.toString(16).toByteArray(Charsets.ISO_8859_1))
                        out.write("\r\n".toByteArray(Charsets.ISO_8859_1))
                    }
                    out.write(buffer, 0, count)
                    if (chunked) out.write("\r\n".toByteArray(Charsets.ISO_8859_1))
                    out.flush()
                }
                if (chunked) out.write("0\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                out.flush()
            }
        } catch (error: Exception) {
            AppLogger.w(TAG, "Cronet relay failed: host=${hostOf(target)}", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(STREAM_COPY_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() > maxBytes - count) throw RuntimeException("Chromium playlist is too large to buffer")
            output.write(buffer, 0, count)
        }
    }

    private fun collectStream(transfer: StreamTransfer, maxBytes: Int = Int.MAX_VALUE): ByteArray {
        val output = ByteArrayOutputStream()
        consumeStream(transfer) { bytes ->
            if (output.size() > maxBytes - bytes.size) throw RuntimeException("Browser response is too large to buffer")
            output.write(bytes)
        }
        return output.toByteArray()
    }

    private fun consumeStream(transfer: StreamTransfer, consume: (ByteArray) -> Unit) {
        while (true) {
            when (val event = transfer.events.poll(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                ?: throw RuntimeException("Browser stream stalled")) {
                is StreamEvent.Data -> consume(event.bytes)
                StreamEvent.Complete -> return
                is StreamEvent.Failure -> throw RuntimeException(event.message)
            }
        }
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

    internal fun rewritePlaylist(
        playlist: String,
        baseUrl: String,
        proxy: (String) -> String,
    ): String =
        playlist.lineSequence().joinToString("\n") { rawLine ->
            val line = rawLine.trimEnd('\r')
            when {
                line.startsWith("#") -> URI_ATTR.replace(line) { match ->
                    "URI=\"${proxy(resolveUrl(baseUrl, match.groupValues[1]))}\""
                }
                line.isBlank() -> line
                else -> proxy(resolveUrl(baseUrl, line))
            }
        }

    private fun fetchViaWebView(session: Session, url: String, range: String?): FetchResult {
        val reqId = UUID.randomUUID().toString()
        val future = CompletableFuture<FetchResult>()
        pending[reqId] = future
        try {
            val script = buildFetchScript(reqId, url, session.headersFor(url), range)
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

    private fun fetchStreamingViaWebView(
        session: Session,
        url: String,
        range: String?,
    ): Pair<String, StreamTransfer> {
        val reqId = UUID.randomUUID().toString()
        val transfer = StreamTransfer { session.lastUsed.set(System.currentTimeMillis()) }
        pendingStreams[reqId] = transfer
        val script = buildStreamingFetchScript(reqId, url, session.headersFor(url), range)
        session.handler.post {
            session.webView.evaluateJavascript(script) { result ->
                AppLogger.d(TAG, "Streaming WebView fetch started: reqId=${reqId.take(8)}, result=$result")
            }
        }
        return reqId to transfer
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
                    $BRIDGE_NAME.onResult($reqIdJson, xhr.status, xhr.getResponseHeader('content-type') || '', xhr.getResponseHeader('content-range') || '', xhr.responseURL || $urlJson, btoa(chunks.join('')));
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

    private fun buildStreamingFetchScript(
        reqId: String,
        url: String,
        headers: Map<String, String>,
        range: String?,
    ): String {
        val headerEntries = if (range != null) headers + ("Range" to range) else headers
        val urlJson = json.encodeToString(url)
        val headersJson = json.encodeToString(headerEntries)
        val reqIdJson = json.encodeToString(reqId)
        return """
            (function(){
              try {
                var controller = new AbortController();
                var timer = null;
                var arm = function(){
                  if (timer) clearTimeout(timer);
                  timer = setTimeout(function(){ controller.abort(); }, $JS_FETCH_TIMEOUT_MS);
                };
                var headers = $headersJson;
                var cleanHeaders = {};
                Object.keys(headers).forEach(function(name){
                  try { var probe = new Headers(); probe.set(name, headers[name]); cleanHeaders[name] = headers[name]; } catch(e) {}
                });
                arm();
                fetch($urlJson, {method:'GET', headers:cleanHeaders, credentials:'include', cache:'no-store', signal:controller.signal}).then(function(response){
                  arm();
                  $BRIDGE_NAME.onStreamStart(
                    $reqIdJson,
                    response.status,
                    response.headers.get('content-type') || '',
                    response.headers.get('content-range') || '',
                    response.headers.get('content-length') || '',
                    response.url || $urlJson
                  );
                  if (!response.body || !response.body.getReader) {
                    return response.arrayBuffer().then(function(buffer){
                      var bytes = new Uint8Array(buffer);
                      for (var i = 0; i < bytes.length; i += 16384) {
                        var part = bytes.subarray(i, i + 16384);
                        $BRIDGE_NAME.onStreamChunk($reqIdJson, btoa(String.fromCharCode.apply(null, part)));
                      }
                      clearTimeout(timer);
                      $BRIDGE_NAME.onStreamComplete($reqIdJson);
                    });
                  }
                  var reader = response.body.getReader();
                  var pump = function(){
                    return reader.read().then(function(result){
                      arm();
                      if (result.done) {
                        clearTimeout(timer);
                        $BRIDGE_NAME.onStreamComplete($reqIdJson);
                        return;
                      }
                      var bytes = result.value;
                      for (var i = 0; i < bytes.length; i += 16384) {
                        var part = bytes.subarray(i, i + 16384);
                        $BRIDGE_NAME.onStreamChunk($reqIdJson, btoa(String.fromCharCode.apply(null, part)));
                      }
                      return pump();
                    });
                  };
                  return pump();
                }).catch(function(error){
                  if (timer) clearTimeout(timer);
                  $BRIDGE_NAME.onStreamError($reqIdJson, String(error));
                });
              } catch(error) { $BRIDGE_NAME.onStreamError($reqIdJson, 'sync: ' + String(error)); }
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

    private fun writeStreamingHeaders(
        out: OutputStream,
        status: Int,
        contentType: String,
        contentLength: Long?,
        contentRange: String?,
        chunked: Boolean,
    ) {
        val reason = if (status == 206) "Partial Content" else if (status in 200..299) "OK" else "Error"
        val headers = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: ${contentType.ifBlank { "application/octet-stream" }}\r\n")
            if (chunked) append("Transfer-Encoding: chunked\r\n")
            else contentLength?.let { append("Content-Length: $it\r\n") }
            append("Accept-Ranges: bytes\r\n")
            contentRange?.let { append("Content-Range: $it\r\n") }
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(headers.toByteArray(Charsets.ISO_8859_1))
    }

    private const val BRIDGE_NAME = "HibikiRelayBridge"
    private const val STREAM_QUEUE_CAPACITY = 32
    private const val STREAM_COPY_BUFFER_SIZE = 32 * 1024
    private const val MAX_PLAYLIST_BYTES = 4 * 1024 * 1024
    private val URI_ATTR = Regex("URI=\"([^\"]+)\"")

    private object Bridge {
        @JavascriptInterface
        fun onResult(
            reqId: String,
            status: Int,
            contentType: String,
            contentRange: String,
            finalUrl: String,
            base64Body: String,
        ) {
            pending.remove(reqId)?.complete(
                FetchResult(
                    status,
                    contentType,
                    contentRange,
                    finalUrl,
                    android.util.Base64.decode(base64Body, android.util.Base64.DEFAULT),
                ),
            )
        }

        @JavascriptInterface
        fun onError(reqId: String, message: String) {
            AppLogger.w(TAG, "WebView fetch rejected: $message")
            pending.remove(reqId)?.completeExceptionally(RuntimeException(message))
        }

        @JavascriptInterface
        fun onStreamStart(
            reqId: String,
            status: Int,
            contentType: String,
            contentRange: String,
            contentLength: String,
            finalUrl: String,
        ) {
            pendingStreams[reqId]?.metadata?.complete(
                StreamMetadata(status, contentType, contentRange, contentLength.toLongOrNull(), finalUrl),
            )
        }

        @JavascriptInterface
        fun onStreamChunk(reqId: String, base64Body: String) {
            val transfer = pendingStreams[reqId] ?: return
            transfer.touch()
            try {
                transfer.events.put(
                    StreamEvent.Data(android.util.Base64.decode(base64Body, android.util.Base64.DEFAULT)),
                )
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        @JavascriptInterface
        fun onStreamComplete(reqId: String) {
            try {
                pendingStreams[reqId]?.events?.put(StreamEvent.Complete)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        @JavascriptInterface
        fun onStreamError(reqId: String, message: String) {
            val transfer = pendingStreams[reqId] ?: return
            transfer.metadata.completeExceptionally(RuntimeException(message))
            try {
                transfer.events.put(StreamEvent.Failure(message))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun safeOrigin(url: String): String? = runCatching {
        url.takeIf(::isAbsoluteUrl)?.let(::originOf)
    }.getOrNull()

    /** Chromium's native network stack is deliberately kept behind the browser-body failure path:
     * normal sources continue using their existing player connection, while CORS-protected streams
     * get Chromium TLS and HTTP behaviour without asking page JavaScript to bypass CORS. */
    private object ChromiumStreamTransport {
        @Volatile private var engine: CronetEngine? = null

        fun open(context: Context, url: String, headers: Map<String, String>, range: String?): HttpURLConnection {
            val connection = (engine(context).openConnection(URL(url)) as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = CRONET_CONNECT_TIMEOUT_MS
                readTimeout = CRONET_READ_TIMEOUT_MS
                headers.forEach { (name, value) ->
                    if (name.isNotBlank() && value.isNotBlank()) runCatching { setRequestProperty(name, value) }
                }
                range?.let { setRequestProperty("Range", it) }
            }
            return connection
        }

        private fun engine(context: Context): CronetEngine = engine ?: synchronized(this) {
            engine ?: run {
                Tasks.await(
                    CronetProviderInstaller.installProvider(context.applicationContext),
                    CRONET_PROVIDER_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                )
                CronetEngine.Builder(context.applicationContext)
                    .enableHttp2(true)
                    .enableQuic(true)
                    .build()
                    .also { engine = it }
            }
        }
    }

    private const val CRONET_CONNECT_TIMEOUT_MS = 15_000
    private const val CRONET_READ_TIMEOUT_MS = 30_000
    private const val CRONET_PROVIDER_TIMEOUT_SECONDS = 10L
}
