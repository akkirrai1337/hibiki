package eu.kanade.tachiyomi.network

import android.content.Context
import android.webkit.WebSettings
import eu.kanade.tachiyomi.network.interceptor.ClearanceInterceptor
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Host implementation of the Aniyomi network service, the desktop twin of the Android build's:
 * cookies shared with the page backend (android.webkit.CookieManager), User-Agent defaulting,
 * Brotli, a small response cache. DNS falls back to DNS-over-HTTPS when the network's resolver
 * refuses a host. A Cloudflare challenge is passed by the person in the application's window (see
 * CloudflareInterceptor); what that earns is sent from then on.
 */
class NetworkHelper(context: Context) {
    private val appContext = context.applicationContext

    val cookieJar: CookieJar = AndroidCookieJar()

    private fun clientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .dns(FallbackDns())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .cache(Cache(File(appContext.cacheDir, "network_cache"), 5L * 1024 * 1024))
        .addInterceptor(ClearanceInterceptor())
        .addInterceptor(FailureRecordingInterceptor())
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
        .addNetworkInterceptor(IgnoreGzipInterceptor())
        .addNetworkInterceptor(BrotliInterceptor)

    /** Without the Cloudflare interceptor, for hosts that must not be loaded in a page. */
    val nonCloudflareClient: OkHttpClient by lazy { clientBuilder().build() }

    /** Posters and icons: a disk cache of their own, as OkHttp wants one cache per directory. */
    val imageClient: OkHttpClient by lazy {
        nonCloudflareClient.newBuilder()
            .cache(Cache(File(appContext.cacheDir, "image_cache"), 100L * 1024 * 1024))
            .build()
    }

    val client: OkHttpClient by lazy { clientBuilder().addInterceptor(CloudflareInterceptor()).build() }

    /** Retained for older sources; [client] already handles Cloudflare. */
    @Deprecated("The regular client handles Cloudflare by default")
    val cloudflareClient: OkHttpClient
        get() = client

    fun defaultUserAgentProvider(): String = WebSettings.getDefaultUserAgent(appContext)

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

        const val FALLBACK_USER_AGENT = WebSettings.DEFAULT_USER_AGENT
    }
}
