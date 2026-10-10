package app.hibiki.apk

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Request
import java.io.ByteArrayInputStream

/**
 * Serves /_hibiki/image?s=<source id>&u=<image url>: an APK source's pictures fetched the way the
 * source fetches its pages - its own headers (User-Agent, Referer) and the shared cookie store, so a
 * site behind Cloudflare or DDoS-Guard that let the source in serves its posters too. Loaded by the
 * WebView directly they came back 403. As in the Kotlin app's covers, the client without the
 * challenge interceptor is used: one blocked picture must never open a challenge WebView.
 * Runs on WebView's IO threads, where blocking is allowed.
 */
object ApkImageProxy {
    const val PATH = "/_hibiki/image"
    private const val FIRST_LOAD_WAIT_MS = 20_000L

    /** The proxied form of [url] for the page, or [url] itself when it is not a web address. */
    fun wrap(sourceId: String, url: String?): String? {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return url
        return "$PATH?s=${android.net.Uri.encode(sourceId)}&u=${android.net.Uri.encode(url)}"
    }

    fun handle(request: WebResourceRequest): WebResourceResponse {
        val url = request.url.getQueryParameter("u")
        // A poster from the page's cache can be asked for before the extensions have loaded, and
        // without its source's headers the site refuses it - the picture stayed broken all session.
        val adapter = request.url.getQueryParameter("s")?.let { sourceId ->
            ApkSourceRegistry.adapter(sourceId) ?: run {
                ApkSourceRegistry.awaitFirstLoad(FIRST_LOAD_WAIT_MS)
                ApkSourceRegistry.adapter(sourceId)
            }
        }
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return failure(400, "Bad Request")
        return try {
            val builder = Request.Builder().url(url)
            (adapter?.catalogue as? AnimeHttpSource)?.headers?.let { builder.headers(it) }
            val response = NetworkHelper.instance().imageClient.newCall(builder.build()).execute()
            if (!response.isSuccessful) {
                response.close()
                return failure(response.code, "Upstream ${response.code}")
            }
            val type = response.header("Content-Type")?.substringBefore(';')?.trim() ?: "image/jpeg"
            WebResourceResponse(type, null, 200, "OK", mapOf("Cache-Control" to "max-age=86400", "Access-Control-Allow-Origin" to "*"), response.body.byteStream())
        } catch (error: Exception) {
            failure(502, "Bad Gateway")
        }
    }

    private fun failure(code: Int, reason: String) =
        WebResourceResponse("text/plain", "utf-8", code, reason, emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
