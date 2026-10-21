package app.hibiki.apkhost

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A page an extension loads is run by the application: commands out, events back, the WebViewClient told. */
class RemotePagesTest {
    private val pages = RemotePages()
    private val context = HostSetup.initialize(Files.createTempDirectory("apk-host-pages").toFile())

    @AfterTest
    fun reset() {
        WebView.backend = null
        CookieManager.getInstance().removeAllCookies(null)
    }

    private fun view(client: WebViewClient): WebView {
        WebView.backend = pages
        return WebView(context).apply { webViewClient = client }
    }

    private fun command(): JSONObject = assertNotNull(pages.nextCommand(2_000), "a command was expected")

    @Test
    fun `loading an address sends a command with the headers, the agent and the stored cookies`() {
        CookieManager.getInstance().setCookie("https://embed.example/", "session=abc; Path=/")
        val view = view(WebViewClient())
        view.settings.setUserAgentString("TestAgent/1.0")
        view.loadUrl("https://embed.example/player?id=1", mapOf("Referer" to "https://site.example/"))
        val load = command()
        assertEquals("load", load.getString("cmd"))
        assertEquals("https://embed.example/player?id=1", load.getString("url"))
        assertEquals("https://site.example/", load.getJSONObject("headers").getString("Referer"))
        assertEquals("TestAgent/1.0", load.getString("userAgent"))
        assertEquals("session=abc", load.getString("cookies"))
        assertTrue(load.getLong("page") > 0)
    }

    @Test
    fun `the requests a page makes reach the client, which is how a video address is caught`() {
        val seen = CopyOnWriteArrayList<String>()
        val finished = CountDownLatch(1)
        val view = view(object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                seen += "${request.method} ${request.url} main=${request.isForMainFrame} referer=${request.requestHeaders["Referer"]}"
                return null
            }

            override fun onPageFinished(view: WebView, url: String) {
                seen += "finished $url"
                finished.countDown()
            }
        })
        view.loadUrl("https://embed.example/player")
        val page = command().getLong("page")
        pages.deliver(JSONObject().put("page", page).put("type", "request").put("url", "https://embed.example/player").put("method", "GET").put("mainFrame", true))
        pages.deliver(JSONObject().put("page", page).put("type", "request").put("url", "https://cdn.example/master.m3u8?t=1").put("method", "GET")
            .put("headers", JSONObject().put("Referer", "https://embed.example/")))
        pages.deliver(JSONObject().put("page", page).put("type", "finished").put("url", "https://embed.example/player"))
        assertTrue(finished.await(2, TimeUnit.SECONDS))
        assertEquals(
            listOf(
                "GET https://embed.example/player main=true referer=null",
                "GET https://cdn.example/master.m3u8?t=1 main=false referer=https://embed.example/",
                "finished https://embed.example/player",
            ),
            seen.toList(),
        )
    }

    @Test
    fun `cookies a page earned go into the store the source's own requests read from`() {
        val finished = CountDownLatch(1)
        val view = view(object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) = finished.countDown()
        })
        view.loadUrl("https://embed.example/")
        val page = command().getLong("page")
        pages.deliver(JSONObject().put("page", page).put("type", "finished").put("url", "https://embed.example/")
            .put("setCookies", org.json.JSONArray(listOf("token=xyz; Path=/", "other=1; Path=/"))))
        assertTrue(finished.await(2, TimeUnit.SECONDS))
        assertEquals("token=xyz; other=1", CookieManager.getInstance().getCookie("https://embed.example/"))
    }

    @Test
    fun `a failed load is reported with its code`() {
        val error = AtomicReference<String>()
        val done = CountDownLatch(1)
        val view = view(object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error2: android.webkit.WebResourceError) {
                error.set("${request.url} ${error2.errorCode} ${error2.description} main=${request.isForMainFrame}")
                done.countDown()
            }
        })
        view.loadUrl("https://down.example/")
        val page = command().getLong("page")
        pages.deliver(JSONObject().put("page", page).put("type", "error").put("url", "https://down.example/").put("code", -105).put("description", "ERR_NAME_NOT_RESOLVED"))
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals("https://down.example/ -105 ERR_NAME_NOT_RESOLVED main=true", error.get())
    }

    @Test
    fun `a script's result comes back to its callback`() {
        val view = view(WebViewClient())
        val result = AtomicReference<String?>("unset")
        val done = CountDownLatch(1)
        view.loadUrl("https://embed.example/")
        val page = command().getLong("page")
        view.evaluateJavascript("document.title") { value -> result.set(value); done.countDown() }
        val evaluate = command()
        assertEquals("evaluate", evaluate.getString("cmd"))
        assertEquals("document.title", evaluate.getString("script"))
        pages.deliver(JSONObject().put("page", page).put("type", "evaluated").put("call", evaluate.getLong("call")).put("result", "\"Hello\""))
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals("\"Hello\"", result.get())
    }

    @Test
    fun `destroying the view closes the page and lets pending scripts go`() {
        val view = view(WebViewClient())
        view.loadUrl("https://embed.example/")
        val page = command().getLong("page")
        val result = AtomicReference<String?>("unset")
        view.evaluateJavascript("1") { result.set(it) }
        command()
        view.destroy()
        assertEquals("close", command().getString("cmd"))
        assertNull(result.get(), "a script that can no longer answer is told so")
        // Events for a page that is gone are ignored, not thrown.
        pages.deliver(JSONObject().put("page", page).put("type", "finished").put("url", "x"))
        // And a destroyed view starts nothing new.
        view.loadUrl("https://embed.example/again")
        assertNull(pages.nextCommand(100))
    }

    @Test
    fun `without a backend nothing loads and scripts answer nothing`() {
        WebView.backend = null
        val view = WebView(context)
        view.loadUrl("https://embed.example/")
        val result = AtomicReference<String?>("unset")
        view.evaluateJavascript("1") { result.set(it) }
        assertNull(result.get())
    }

    @Test
    fun `the application's stream carries commands out and events back over HTTP`() {
        val server = Server(ApkHost(Files.createTempDirectory("apk-host-pages-server").toFile()), "secret", pages).also { it.start() }
        try {
            val stream = (URL("http://127.0.0.1:${server.port}/pages/commands").openConnection() as HttpURLConnection).apply {
                setRequestProperty(Server.TOKEN_HEADER, "secret")
                readTimeout = 10_000
            }
            val reader = BufferedReader(InputStreamReader(stream.inputStream))
            val finished = CountDownLatch(1)
            val view = view(object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) = finished.countDown()
            })
            view.loadUrl("https://embed.example/stream")
            var line = reader.readLine()
            while (line != null && line.isBlank()) line = reader.readLine()
            val load = JSONObject(line)
            assertEquals("https://embed.example/stream", load.getString("url"))

            val event = (URL("http://127.0.0.1:${server.port}/pages/event").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty(Server.TOKEN_HEADER, "secret")
            }
            event.outputStream.use { it.write(JSONObject().put("page", load.getLong("page")).put("type", "finished").put("url", "https://embed.example/stream").toString().toByteArray()) }
            assertEquals(200, event.responseCode)
            assertTrue(finished.await(2, TimeUnit.SECONDS))

            val refused = URL("http://127.0.0.1:${server.port}/pages/commands").openConnection() as HttpURLConnection
            assertEquals(403, refused.responseCode)
        } finally {
            server.stop()
        }
    }

    class Bridge {
        val calls = CopyOnWriteArrayList<String>()
        val called = CountDownLatch(1)

        @android.webkit.JavascriptInterface
        fun onResult(url: String?, count: Int) {
            calls += "onResult $url $count on ${Thread.currentThread().name}"
            called.countDown()
        }

        fun notExposed(value: String?) {
            calls += "notExposed $value"
        }
    }

    @Test
    fun `an interface added before loading reaches the page first, and the page's calls reach its annotated methods`() {
        val view = view(WebViewClient())
        val bridge = Bridge()
        view.addJavascriptInterface(bridge, "SenshiBridge")
        view.loadUrl("https://embed.example/")
        val added = command()
        assertEquals("interface", added.getString("cmd"))
        assertEquals("SenshiBridge", added.getString("name"))
        val methods = added.getJSONArray("methods")
        assertEquals(listOf("onResult"), (0 until methods.length()).map { methods.getString(it) })
        assertEquals("load", command().getString("cmd"))
        val page = added.getLong("page")
        pages.deliver(JSONObject().put("page", page).put("type", "jsCall").put("name", "SenshiBridge").put("method", "notExposed").put("args", org.json.JSONArray().put("x")))
        pages.deliver(JSONObject().put("page", page).put("type", "jsCall").put("name", "SenshiBridge").put("method", "onResult").put("args", org.json.JSONArray().put("https://cdn.example/a.m3u8").put(3)))
        assertTrue(bridge.called.await(2, TimeUnit.SECONDS))
        assertEquals(listOf("onResult https://cdn.example/a.m3u8 3 on JavaBridge"), bridge.calls.toList())
    }

    @Test
    fun `an interface added to a page already open is sent at once`() {
        val view = view(WebViewClient())
        view.loadUrl("https://embed.example/")
        assertEquals("load", command().getString("cmd"))
        view.addJavascriptInterface(Bridge(), "HexaCap")
        assertEquals("interface", command().getString("cmd"))
    }
}
