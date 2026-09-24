package eu.kanade.tachiyomi.network

import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class RateLimitInterceptorTest {
    private val ok = Interceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
    }

    private fun elapsedFor(client: OkHttpClient, urls: List<String>): Long {
        val start = System.nanoTime()
        urls.forEach { client.newCall(Request.Builder().url(it).build()).execute().close() }
        return (System.nanoTime() - start) / 1_000_000
    }

    @Test
    fun `third request within the window waits for the window to slide`() {
        val client = OkHttpClient.Builder().rateLimit(2, 300.milliseconds).addInterceptor(ok).build()
        val elapsed = elapsedFor(client, List(3) { "https://example.com/$it" })
        assertTrue("expected >= 250ms, was $elapsed", elapsed >= 250)
    }

    @Test
    fun `host limiter ignores other hosts`() {
        val client = OkHttpClient.Builder()
            .rateLimitHost("https://limited.example", 1, 500.milliseconds)
            .addInterceptor(ok)
            .build()
        val elapsed = elapsedFor(client, List(5) { "https://other.example/$it" })
        assertTrue("expected < 200ms, was $elapsed", elapsed < 200)
    }
}
