package eu.kanade.tachiyomi.network

import android.content.Context
import android.webkit.WebSettings
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Host implementation of the Aniyomi network service. The client mirrors Aniyomi's: cookies shared
 * with the WebView, User-Agent defaulting, Brotli, a small response cache, and Cloudflare
 * challenges solved through a hidden WebView. DNS falls back to DNS-over-HTTPS when the network's
 * resolver refuses a host.
 */
class NetworkHelper(context: Context) {
    private val appContext = context.applicationContext

    /** Shared with the WebView, so a solved Cloudflare challenge also serves OkHttp. Falls back to memory. */
    val cookieJar: CookieJar by lazy { runCatching { AndroidCookieJar() }.getOrElse { MemoryCookieJar() } }

    private fun clientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .dns(FallbackDns())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .cache(Cache(File(appContext.cacheDir, "network_cache"), 5L * 1024 * 1024))
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
        .addNetworkInterceptor(IgnoreGzipInterceptor())
        .addNetworkInterceptor(BrotliInterceptor)

    /** Without the Cloudflare interceptor, for hosts that must not be loaded in a WebView. */
    val nonCloudflareClient: OkHttpClient by lazy { clientBuilder().build() }

    val client: OkHttpClient by lazy {
        clientBuilder().apply {
            (cookieJar as? AndroidCookieJar)?.let { jar ->
                addInterceptor(CloudflareInterceptor(appContext, jar, ::defaultUserAgentProvider))
            }
        }.build()
    }

    /** Retained for older sources; [client] already handles Cloudflare. */
    @Deprecated("The regular client handles Cloudflare by default")
    val cloudflareClient: OkHttpClient
        get() = client

    fun defaultUserAgentProvider(): String = runCatching {
        WebSettings.getDefaultUserAgent(appContext)
    }.getOrDefault(FALLBACK_USER_AGENT)

    companion object {
        @Volatile
        private var sharedInstance: NetworkHelper? = null

        fun initialize(context: Context) {
            if (sharedInstance == null) {
                synchronized(this) {
                    if (sharedInstance == null) sharedInstance = NetworkHelper(context.applicationContext)
                }
            }
        }

        /** One shared instance, so every source reuses the same connection pool and cookie jar. */
        fun instance(): NetworkHelper =
            sharedInstance ?: error("NetworkHelper.initialize(context) must be called first")

        const val FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
    }
}

private class MemoryCookieJar : CookieJar {
    private val cookies = mutableMapOf<CookieKey, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        this.cookies.entries.removeAll { it.value.expiresAt <= now }
        cookies.forEach { cookie ->
            val key = CookieKey(cookie.name, cookie.domain, cookie.path)
            if (cookie.expiresAt <= now) this.cookies.remove(key) else this.cookies[key] = cookie
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.entries.removeAll { it.value.expiresAt <= now }
        return cookies.values.filter { it.matches(url) }
    }

    private data class CookieKey(val name: String, val domain: String, val path: String)
}
