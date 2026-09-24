package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Solves Cloudflare's browser challenge the way Aniyomi does: when a request is answered with the
 * challenge page, the URL is loaded in a hidden WebView until it earns a `cf_clearance` cookie
 * (shared with OkHttp through [AndroidCookieJar]), then the request is retried.
 */
class CloudflareInterceptor(
    private val context: Context,
    private val cookieJar: AndroidCookieJar,
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {
    private val executor = ContextCompat.getMainExecutor(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!shouldIntercept(response)) return response
        if (!supportsWebView()) return response

        try {
            response.close()
            cookieJar.remove(request.url, COOKIE_NAMES, 0)
            val oldCookie = cookieJar.get(request.url).firstOrNull { it.name == "cf_clearance" }
            resolveWithWebView(request, oldCookie)
            return chain.proceed(request)
        } catch (error: CloudflareBypassException) {
            // OkHttp's enqueue only handles IOException; anything else would crash the app.
            throw IOException("Cloudflare challenge could not be solved", error)
        } catch (error: Exception) {
            throw if (error is IOException) error else IOException(error)
        }
    }

    private fun shouldIntercept(response: Response): Boolean {
        if (response.code !in ERROR_CODES || response.header("Server") !in SERVER_CHECK) return false
        val document = Jsoup.parse(response.peekBody(Long.MAX_VALUE).string(), response.request.url.toString())
        // Solve only a captcha/JS challenge, not a geo block.
        return document.getElementById("challenge-error-title") != null ||
            document.getElementById("challenge-error-text") != null
    }

    private fun supportsWebView(): Boolean = runCatching { CookieManager.getInstance(); true }.getOrDefault(false)

    private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?) {
        // OkHttp has no asynchronous interceptors, so this thread waits for the WebView.
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        var challengeFound = false
        var bypassed = false
        val requestUrl = originalRequest.url.toString()
        val headers = safeHeaders(originalRequest.headers)

        executor.execute {
            val view = WebView(context).apply {
                CookieManager.getInstance().acceptThirdPartyCookies(this)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                // Chromium resets an empty User-Agent to its own default, so never send one.
                settings.userAgentString = originalRequest.header("User-Agent") ?: defaultUserAgentProvider()
            }
            webView = view
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val cleared = cookieJar.get(requestUrl.toHttpUrl())
                        .firstOrNull { it.name == "cf_clearance" }
                        .let { it != null && it != oldCookie }
                    if (cleared) {
                        bypassed = true
                        latch.countDown()
                    }
                    // The first load did not show the challenge: nothing to wait for.
                    if (url == requestUrl && !challengeFound) latch.countDown()
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        if (errorResponse?.statusCode in ERROR_CODES) challengeFound = true else latch.countDown()
                    }
                }
            }
            view.loadUrl(requestUrl, headers)
        }

        latch.await(CHALLENGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        executor.execute { webView?.run { stopLoading(); destroy() } }
        if (!bypassed) throw CloudflareBypassException()
    }

    private fun safeHeaders(headers: Headers): Map<String, String> = headers
        .filter { (name, value) -> isRequestHeaderSafe(name, value) }
        .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
        .mapValues { it.value.firstOrNull().orEmpty() }

    private class CloudflareBypassException : Exception()

    private companion object {
        val ERROR_CODES = listOf(403, 503)
        val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        val COOKIE_NAMES = listOf("cf_clearance")
        const val CHALLENGE_TIMEOUT_SECONDS = 30L

        // Based on IsRequestHeaderSafe in Chromium's header_util.cc; unsafe headers make WebView
        // fail with net::ERR_INVALID_ARGUMENT.
        val UNSAFE_HEADERS = setOf(
            "content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive",
            "transfer-encoding", "set-cookie",
        )

        fun isRequestHeaderSafe(rawName: String, rawValue: String): Boolean {
            val name = rawName.lowercase(Locale.ENGLISH)
            val value = rawValue.lowercase(Locale.ENGLISH)
            if (name in UNSAFE_HEADERS || name.startsWith("proxy-")) return false
            return !(name == "connection" && value == "upgrade")
        }
    }
}
