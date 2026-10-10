package app.hibiki

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Request

/**
 * The page's pictures from other sites - script sources' posters and icons - fetched by the app with
 * the Referer a page on the picture's own site would send. Loaded by the WebView itself they carry
 * this app's page as Referer, and image hosts that guard against hotlinking (AniTube's, for one)
 * answer that with 403: the poster flashed and was gone. Desktop does the same in
 * shared/imageRequestHeaders.ts, which has the hosts this was measured against.
 * Runs on WebView's IO threads, where blocking is allowed.
 */
object WebImageProxy {
    /** Whether this request is a picture this proxy should take: a GET for an image from the web. */
    fun accepts(request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme
        if (request.method != "GET" || (scheme != "https" && scheme != "http")) return false
        val accept = request.requestHeaders.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value ?: return false
        return accept.startsWith("image/")
    }

    /** The picture, or null to let the WebView load it itself when the app could not reach it. */
    fun handle(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        return try {
            val builder = Request.Builder().url(url.toString())
            for ((name, value) in request.requestHeaders) {
                if (name.equals("Referer", ignoreCase = true) || name.equals("Origin", ignoreCase = true)) continue
                builder.header(name, value)
            }
            builder.header("Referer", "${url.scheme}://${url.authority}/")
            val response = NetworkHelper.instance().imageClient.newCall(builder.build()).execute()
            val type = response.header("Content-Type")?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() } ?: "image/jpeg"
            val reason = response.message.ifBlank { if (response.isSuccessful) "OK" else "Error" }
            WebResourceResponse(type, null, response.code, reason, mapOf("Cache-Control" to "max-age=86400", "Access-Control-Allow-Origin" to "*"), response.body.byteStream())
        } catch (error: Exception) {
            null
        }
    }
}
