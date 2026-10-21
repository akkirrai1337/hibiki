package eu.kanade.tachiyomi.network.interceptor

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException

/**
 * Recognises Cloudflare's browser challenge. The Android build tries to pass it in a hidden WebView
 * and falls back to asking the person; here the person is always asked - the application opens the
 * site in a window of its own (core/extensions/cloudflare.ts), and the clearance that earns reaches
 * this process through [Clearances]. The error's text is what the application recognises.
 */
class CloudflareInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!shouldIntercept(response)) return response
        val url = request.url.toString()
        response.close()
        throw CloudflareChallengeException(url)
    }

    private fun shouldIntercept(response: Response): Boolean {
        if (response.code !in ERROR_CODES || response.header("Server") !in SERVER_CHECK) return false
        // A source's own interceptor, run closer to the network than this one, may already have read and
        // closed the body; a page that can no longer be read cannot be told to be a challenge.
        val html = runCatching { response.peekBody(Long.MAX_VALUE).string() }.getOrNull() ?: return false
        val document = Jsoup.parse(html, response.request.url.toString())
        // A captcha/JS challenge, not a geo block.
        return document.getElementById("challenge-error-title") != null ||
            document.getElementById("challenge-error-text") != null
    }

    private companion object {
        val ERROR_CODES = listOf(403, 503)
        val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
    }
}

/**
 * A page was answered with a Cloudflare challenge that needs a person. The message is a contract:
 * the application finds the address in it (challengeUrlInError in core/extensions/cloudflare.ts)
 * and offers the check.
 */
class CloudflareChallengeException(val url: String, cause: Throwable? = null) :
    IOException("Cloudflare challenge could not be solved for $url", cause)

/**
 * What a passed check leaves: the clearance cookies and the User-Agent they are bound to (Cloudflare
 * ties a clearance to the browser that earned it). Requests to the site, and its subdomains, carry
 * that User-Agent from then on; the cookies themselves are in the shared cookie store.
 */
object Clearances {
    private val userAgents = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun remember(url: String, userAgent: String) {
        val host = url.toHttpUrlOrNull()?.host ?: return
        userAgents[host] = userAgent
    }

    /** The User-Agent a clearance for [host] (or a parent domain of it) was earned with. */
    fun userAgentFor(host: String): String? {
        val labels = host.split('.')
        // The host itself first, then its parent domains - but never a bare top-level domain.
        for (i in 0 until maxOf(1, labels.size - 1)) userAgents[labels.drop(i).joinToString(".")]?.let { return it }
        return null
    }

    fun clear() = userAgents.clear()
}

/** Sends a site's requests with the User-Agent its clearance was earned with, whatever the source set. */
class ClearanceInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val userAgent = Clearances.userAgentFor(request.url.host) ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("User-Agent", userAgent).build())
    }
}
