package app.hibiki.apkhost

import app.hibiki.apk.ApkImageProxy
import app.hibiki.apk.ApkPreferences
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * The host's calls over HTTP, for the application that spawned it: loopback only, and every
 * request must carry the token that application was given at start - another program on the
 * machine cannot talk to it. Bodies and answers are JSON in the shapes the Android plugin uses
 * (shared/types.ts); a failure is a 500 with {"error": message}.
 *
 *   GET  /extensions                      installed extensions and the sources ready to use
 *   POST /install      {url, packageName?}
 *   POST /trust        {packageName, fingerprint}
 *   POST /uninstall    {packageName}
 *   POST /clearance    {url, userAgent, cookies}   what a passed Cloudflare check earned
 *   POST /source/<id>/<method>  {…}        search, latest, getById, playbackGroups, playerLinks,
 *                                          filterCatalog, readSettings, writeSetting
 *   GET  /image?s=<source>&u=<url>        a picture fetched the way the source fetches
 *   GET  /icon?p=<package>                an extension's icon
 *   GET  /pages/commands                  a stream (a JSON command a line) the application keeps open to run
 *                                         the pages extensions load; POST /pages/event reports back (RemotePages)
 */
class Server(private val host: ApkHost, private val token: String, private val pages: RemotePages = RemotePages()) {
    private val http = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    val address: InetSocketAddress get() = http.address
    val port: Int get() = address.port

    init {
        http.executor = Executors.newCachedThreadPool { runnable -> Thread(runnable, "apk-host-http").apply { isDaemon = true } }
        http.createContext("/") { exchange -> exchange.use(::handle) }
    }

    fun start() = http.start()

    fun stop() = http.stop(0)

    private fun handle(exchange: HttpExchange) {
        if (exchange.requestHeaders.getFirst(TOKEN_HEADER) != token) {
            return reply(exchange, 403, JSONObject().put("error", "Forbidden"))
        }
        try {
            val path = exchange.requestURI.rawPath
            val method = exchange.requestMethod
            when {
                method == "GET" && path == "/extensions" -> reply(exchange, 200, host.describe())
                method == "GET" && path == "/image" -> image(exchange)
                method == "GET" && path == "/icon" -> icon(exchange)
                method == "GET" && path == "/pages/commands" -> commandStream(exchange)
                method == "POST" && path == "/pages/event" -> {
                    pages.deliver(body(exchange))
                    reply(exchange, 200, JSONObject())
                }
                method == "POST" && path == "/install" -> {
                    val body = body(exchange)
                    val installed = runBlocking { host.install(body.getString("url"), body.optString("packageName").ifEmpty { null }) }
                    reply(exchange, 200, JSONObject()
                        .put("packageName", installed.packageName)
                        .put("name", installed.name)
                        .put("versionName", installed.versionName)
                        .put("fingerprint", installed.signingFingerprint ?: JSONObject.NULL)
                        .put("trusted", true))
                }
                method == "POST" && path == "/trust" -> {
                    val body = body(exchange)
                    host.trust(body.getString("packageName"), body.getString("fingerprint"))
                    reply(exchange, 200, host.describe())
                }
                method == "POST" && path == "/clearance" -> {
                    val body = body(exchange)
                    val cookies = body.getJSONObject("cookies")
                    host.applyClearance(body.getString("url"), body.getString("userAgent"), cookies.keys().asSequence().associateWith { cookies.getString(it) })
                    reply(exchange, 200, JSONObject())
                }
                method == "POST" && path == "/clearance/reset" -> {
                    host.resetClearances()
                    reply(exchange, 200, JSONObject())
                }
                method == "POST" && path == "/uninstall" -> {
                    host.uninstall(body(exchange).getString("packageName"))
                    reply(exchange, 200, host.describe())
                }
                method == "POST" && path.startsWith("/source/") -> {
                    val parts = path.removePrefix("/source/").split('/', limit = 2)
                    require(parts.size == 2) { "Expected /source/<id>/<method>" }
                    val sourceId = java.net.URLDecoder.decode(parts[0], Charsets.UTF_8)
                    reply(exchange, 200, runBlocking { call(sourceId, parts[1], body(exchange)) })
                }
                else -> reply(exchange, 404, JSONObject().put("error", "Not found"))
            }
        } catch (error: Throwable) {
            var cause: Throwable = error
            if (error is java.lang.reflect.InvocationTargetException && error.cause != null) cause = error.cause!!
            reply(exchange, if (error is IllegalArgumentException || error is org.json.JSONException) 400 else 500,
                JSONObject().put("error", cause.message ?: cause.javaClass.simpleName))
        }
    }

    /** One of the source's calls, answered as the shared JSON shapes: a list comes as {"items": […]}. */
    private suspend fun call(sourceId: String, method: String, body: JSONObject): Any {
        val adapter = host.adapter(sourceId)
        fun items(array: JSONArray) = JSONObject().put("items", array)
        return when (method) {
            "search" -> items(adapter.search(body.optJSONObject("request") ?: JSONObject()))
            "latest" -> items(adapter.latest(body.optInt("limit", 24)))
            "getById" -> adapter.getById(body.getString("id"))
            "playbackGroups" -> items(adapter.playbackGroups(body.getString("titleId")))
            "playerLinks" -> items(adapter.playerLinks(body.optString("titleId").ifEmpty { null }, body.getString("episodeId")))
            "filterCatalog" -> adapter.filterCatalog()
            "readSettings" -> ApkPreferences.values(host.application, adapter)
            "writeSetting" -> {
                ApkPreferences.write(host.application, adapter, body.getString("key"), if (body.isNull("value")) null else body.getString("value"))
                JSONObject()
            }
            else -> throw IllegalArgumentException("APK sources do not support $method()")
        }
    }


    /**
     * Holds the connection open and writes a command a line as pages ask for them; ends when the
     * application disconnects (the write fails), which drops every page it was running.
     */
    private fun commandStream(exchange: HttpExchange) {
        exchange.responseHeaders.add("Content-Type", "application/x-ndjson; charset=utf-8")
        exchange.sendResponseHeaders(200, 0)
        val out = exchange.responseBody
        try {
            while (true) {
                // A line of nothing when idle keeps the connection honest: a dead peer shows as a failed write.
                val line = pages.nextCommand(15_000)?.toString() ?: ""
                out.write((line + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
            }
        } catch (_: java.io.IOException) {
            pages.dropAll()
        }
    }
    // --- pictures -----------------------------------------------------------------------------------------

    private fun query(exchange: HttpExchange, name: String): String? =
        exchange.requestURI.rawQuery.orEmpty().split('&').firstNotNullOfOrNull { pair ->
            val key = pair.substringBefore('=')
            if (java.net.URLDecoder.decode(key, Charsets.UTF_8) == name) java.net.URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8) else null
        }

    /**
     * A picture fetched the way the source fetches its pages - its own headers and the shared cookie
     * store, so a site that let the source in serves its posters too. The client without the
     * challenge interceptor: one blocked picture must never open a challenge page.
     */
    private fun image(exchange: HttpExchange) {
        val url = query(exchange, "u")
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return reply(exchange, 400, JSONObject().put("error", "Bad Request"))
        val adapter = query(exchange, "s")?.let { host.registry.adapter(it) }
        try {
            val builder = Request.Builder().url(url)
            (adapter?.catalogue as? AnimeHttpSource)?.headers?.let { builder.headers(it) }
            NetworkHelper.instance().imageClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return reply(exchange, response.code, JSONObject().put("error", "Upstream ${response.code}"))
                val type = response.header("Content-Type")?.substringBefore(';')?.trim() ?: "image/jpeg"
                val bytes = response.body.bytes()
                exchange.responseHeaders.add("Content-Type", type)
                exchange.responseHeaders.add("Cache-Control", "max-age=86400")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        } catch (error: java.io.IOException) {
            reply(exchange, 502, JSONObject().put("error", "Bad Gateway"))
        }
    }

    private fun icon(exchange: HttpExchange) {
        val packageName = query(exchange, "p") ?: return reply(exchange, 400, JSONObject().put("error", "Bad Request"))
        val file: File = host.store.find(packageName)?.iconFile ?: return reply(exchange, 404, JSONObject().put("error", "Not found"))
        val bytes = file.readBytes()
        exchange.responseHeaders.add("Content-Type", "image/png")
        exchange.responseHeaders.add("Cache-Control", "max-age=86400")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    // --- plumbing -----------------------------------------------------------------------------------------

    private fun body(exchange: HttpExchange): JSONObject {
        val text = exchange.requestBody.readBytes().decodeToString()
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun reply(exchange: HttpExchange, status: Int, json: Any) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    companion object {
        const val TOKEN_HEADER = "X-Hibiki-Token"
    }
}
