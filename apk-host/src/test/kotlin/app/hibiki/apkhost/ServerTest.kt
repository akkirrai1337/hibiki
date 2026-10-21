package app.hibiki.apkhost

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The HTTP face of the host, as the application talks to it: token, routes, error mapping. */
class ServerTest {
    private val dir = Files.createTempDirectory("apk-host-server").toFile()
    private val sample: File? = System.getProperty("apk.samples")?.let(::File)
        ?.listFiles { f -> f.name.startsWith("aniyomi-ru.animevost-") }?.firstOrNull()
    private val host = ApkHost(dir)
    private val server = Server(host, "secret").also { it.start() }

    @AfterTest
    fun stop() = server.stop()

    /** What came back: the status and the raw body (the JDK's own client, so the test runs on a trimmed runtime too). */
    private class Answer(val status: Int, val bytes: ByteArray) {
        fun statusCode() = status
        fun body() = bytes.decodeToString()
    }

    private fun send(method: String, path: String, body: String? = null, token: String? = "secret"): Answer {
        val connection = URL("http://127.0.0.1:${server.port}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        if (token != null) connection.setRequestProperty(Server.TOKEN_HEADER, token)
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        val status = connection.responseCode
        val stream = if (status >= 400) connection.errorStream else connection.inputStream
        return Answer(status, stream?.use { it.readBytes() } ?: ByteArray(0))
    }

    private fun install() = host.install(dir.resolve("in.apk").also { sample!!.copyTo(it, overwrite = true) }, null)

    @Test
    fun `a request without the token is refused`() {
        assertEquals(403, send("GET", "/extensions", token = null).statusCode())
        assertEquals(403, send("GET", "/extensions", token = "wrong").statusCode())
        assertEquals(200, send("GET", "/extensions").statusCode())
    }

    @Test
    fun `it listens on the loopback address only`() {
        assertTrue(server.address.address.isLoopbackAddress)
        assertTrue(server.port > 0)
    }

    @Test
    fun `unknown routes and sources are told apart`() {
        assertEquals(404, send("GET", "/nope").statusCode())
        val unknown = send("POST", "/source/apk%3A123/search", "{}")
        assertEquals(500, unknown.statusCode())
        assertTrue(JSONObject(unknown.body()).getString("error").contains("not installed"))
    }

    @Test
    fun `an installed extension is listed with its sources and answers calls`() {
        if (sample == null) return
        val installed = install()
        val listed = JSONObject(send("GET", "/extensions").body())
        val extension = listed.getJSONArray("extensions").getJSONObject(0)
        assertEquals(installed.packageName, extension.getString("packageName"))
        assertTrue(extension.getBoolean("trusted"))
        val sources = listed.getJSONArray("sources")
        assertTrue(sources.length() >= 1)
        val sourceId = sources.getJSONObject(0).getString("id")
        assertTrue(sourceId.startsWith("apk:"))

        val encoded = java.net.URLEncoder.encode(sourceId, Charsets.UTF_8)
        val filters = send("POST", "/source/$encoded/filterCatalog", "{}")
        assertEquals(200, filters.statusCode(), filters.body())
        assertTrue(JSONObject(filters.body()).getJSONArray("sortOptions").length() >= 1)
        assertEquals(200, send("POST", "/source/$encoded/readSettings", "{}").statusCode())

        val unsupported = send("POST", "/source/$encoded/explode", "{}")
        assertEquals(400, unsupported.statusCode())
        val missing = send("POST", "/source/$encoded/getById", "{}")
        assertEquals(400, missing.statusCode(), "a missing argument is the caller's mistake")
    }

    @Test
    fun `icons are served and trust and removal work over the wire`() {
        if (sample == null) return
        val installed = install()
        val icon = send("GET", "/icon?p=${installed.packageName}")
        // Only an extension that ships a PNG icon has one; whichever it is, the answer is well-formed.
        assertTrue(icon.statusCode() == 200 || icon.statusCode() == 404)
        if (icon.statusCode() == 200) assertTrue(icon.bytes.size > 8 && icon.bytes[1] == 'P'.code.toByte())

        val wrong = send("POST", "/trust", JSONObject().put("packageName", installed.packageName).put("fingerprint", "00").toString())
        assertEquals(500, wrong.statusCode())
        assertTrue(JSONObject(wrong.body()).getString("error").contains("signature changed"))

        val removed = send("POST", "/uninstall", JSONObject().put("packageName", installed.packageName).toString())
        assertEquals(200, removed.statusCode())
        assertEquals(0, JSONObject(removed.body()).getJSONArray("extensions").length())
    }

    @Test
    fun `an image address that is not a web address is refused`() {
        assertEquals(400, send("GET", "/image?u=file%3A%2F%2F%2Fetc%2Fpasswd").statusCode())
        assertEquals(400, send("GET", "/image").statusCode())
    }

    @Test
    fun `install over plain http is refused`() {
        val answer = send("POST", "/install", JSONObject().put("url", "http://example.com/x.apk").toString())
        assertEquals(400, answer.statusCode())
        assertTrue(JSONObject(answer.body()).getString("error").contains("https"))
    }
}
