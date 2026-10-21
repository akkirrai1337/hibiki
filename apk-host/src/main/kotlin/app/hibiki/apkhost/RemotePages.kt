package app.hibiki.apkhost

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The pages extensions load (android.webkit.WebView), run by the application: this process has no
 * browser, so each page is a hidden window on the other side. Commands go out over a stream the
 * application keeps open (GET /pages/commands, one JSON object a line), and what the page does comes
 * back as events (POST /pages/event) that reach the extension's WebViewClient the way a WebView's
 * would: every request the page makes (shouldInterceptRequest, which extensions use to catch a video's
 * address), the page finishing, a failed load.
 *
 * Commands: {"cmd":"load","page":1,"url":…,"headers":{…},"userAgent":…,"cookies":"a=b; c=d"}
 *           {"cmd":"loadData","page":1,"baseUrl":…,"data":…,"mimeType":…,"userAgent":…}
 *           {"cmd":"evaluate","page":1,"call":7,"script":…}  {"cmd":"stop","page":1}  {"cmd":"close","page":1}
 * Events:   {"page":1,"type":"request","url":…,"method":…,"headers":{…},"mainFrame":true}
 *           {"page":1,"type":"finished","url":…}  {"page":1,"type":"error","url":…,"code":-105,"description":…}
 *           {"page":1,"type":"evaluated","call":7,"result":"\"json text\""}
 */
class RemotePages : WebView.Backend {
    private val commands = LinkedBlockingQueue<JSONObject>()
    private val pages = ConcurrentHashMap<Long, RemotePage>()
    private val nextPage = AtomicLong(1)

    override fun open(view: WebView): WebView.Page = RemotePage(nextPage.getAndIncrement(), view).also { pages[it.id] = it }

    /** The next command for the application, or null if none came in [timeoutMs]. */
    fun nextCommand(timeoutMs: Long): JSONObject? = commands.poll(timeoutMs, TimeUnit.MILLISECONDS)

    /** What the application reports about one of the pages. A page that is gone is ignored. */
    fun deliver(event: JSONObject) {
        val page = pages[event.optLong("page")] ?: return
        page.handle(event)
    }

    /** The application went away: every page is over. */
    fun dropAll() {
        pages.values.forEach { it.abandon() }
        pages.clear()
        commands.clear()
    }

    private inner class RemotePage(val id: Long, private val view: WebView) : WebView.Page {
        private val evaluations = ConcurrentHashMap<Long, ValueCallback<String>?>()
        private val nextCall = AtomicLong(1)
        // Android reports a page's progress on its main thread; an extension may rely on that.
        private val mainThread = android.os.Handler(android.os.Looper.getMainLooper())

        private fun send(command: JSONObject) {
            commands.put(command.put("page", id))
        }

        override fun loadUrl(url: String, headers: Map<String, String>) {
            send(JSONObject().put("cmd", "load").put("url", url).put("headers", JSONObject(headers))
                .put("userAgent", view.settings.userAgentString)
                // The shared store, so a clearance or a login the source's own requests earned applies to the page.
                .put("cookies", CookieManager.getInstance().getCookie(url) ?: ""))
        }

        override fun loadData(baseUrl: String?, data: String, mimeType: String?) {
            send(JSONObject().put("cmd", "loadData").put("baseUrl", baseUrl ?: "about:blank").put("data", data)
                .put("mimeType", mimeType ?: "text/html").put("userAgent", view.settings.userAgentString))
        }

        override fun evaluate(script: String, callback: ValueCallback<String>?) {
            val call = nextCall.getAndIncrement()
            evaluations[call] = callback
            send(JSONObject().put("cmd", "evaluate").put("call", call).put("script", script))
        }

        override fun stop() = send(JSONObject().put("cmd", "stop"))

        override fun close() {
            pages.remove(id)
            send(JSONObject().put("cmd", "close"))
            abandon()
        }

        fun abandon() {
            evaluations.keys.toList().forEach { evaluations.remove(it)?.onReceiveValue(null) }
        }

        fun handle(event: JSONObject) {
            val client = view.webViewClient
            when (event.optString("type")) {
                "request" -> client.shouldInterceptRequest(view, PageRequest(event))
                "finished" -> {
                    // The page's cookies (a challenge's clearance, a session) go into the store the source's own
                    // requests read from.
                    event.optJSONArray("setCookies")?.let { applyCookies(event.optString("url"), it) }
                    val url = event.optString("url")
                    mainThread.post { client.onPageFinished(view, url) }
                }
                "error" -> {
                    val request = PageRequest(event, mainFrame = true)
                    val error = PageError(event.optInt("code", -1), event.optString("description"))
                    mainThread.post { client.onReceivedError(view, request, error) }
                }
                "evaluated" -> evaluations.remove(event.optLong("call"))?.onReceiveValue(if (event.isNull("result")) null else event.optString("result"))
            }
        }

        private fun applyCookies(url: String, cookies: JSONArray) {
            val manager = CookieManager.getInstance()
            for (i in 0 until cookies.length()) manager.setCookie(url, cookies.getString(i))
        }
    }

    private class PageRequest(event: JSONObject, private val mainFrame: Boolean = event.optBoolean("mainFrame")) : WebResourceRequest {
        private val url = Uri.parse(event.optString("url"))
        private val method = event.optString("method", "GET")
        private val headers: Map<String, String> = event.optJSONObject("headers")?.let { h -> h.keys().asSequence().associateWith { h.getString(it) } } ?: emptyMap()
        override fun getUrl(): Uri = url
        override fun isForMainFrame(): Boolean = mainFrame
        override fun getMethod(): String = method
        override fun getRequestHeaders(): Map<String, String> = headers
    }

    private class PageError(private val code: Int, private val description: String) : WebResourceError() {
        override fun getErrorCode(): Int = code
        override fun getDescription(): CharSequence = description
    }
}
