package eu.kanade.tachiyomi.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import javax.net.ssl.SSLException

/**
 * A short memory of what went wrong on the extension network client.
 *
 * Extensions routinely catch every failure of a request and just return an empty list, so "no
 * videos" arrives without a cause. Recording failures at the client lets the host say why: a host that
 * does not resolve, a timeout, a blocked (403) or broken (5xx) endpoint.
 */
internal object NetworkFailureLog {
    data class Failure(val atMs: Long, val host: String, val reason: String)

    private const val MAX_ENTRIES = 60
    private val entries = ConcurrentLinkedDeque<Failure>()

    fun record(host: String, reason: String) {
        entries.addLast(Failure(System.currentTimeMillis(), host, reason))
        while (entries.size > MAX_ENTRIES) entries.pollFirst()
    }

    /** Distinct failures recorded since [sinceMs], oldest first. */
    fun since(sinceMs: Long): List<Failure> =
        entries.filter { it.atMs >= sinceMs }.distinctBy { it.host to it.reason }

    /** "host: reason" pairs joined for a message, at most [limit] of them. */
    fun summary(sinceMs: Long, limit: Int = 3): String? =
        since(sinceMs).takeIf { it.isNotEmpty() }
            ?.take(limit)
            ?.joinToString("; ") { "${it.host}: ${it.reason}" }

    fun describe(error: IOException): String = when (error) {
        is UnknownHostException -> "cannot resolve host"
        is SocketTimeoutException -> "timed out"
        is SSLException -> "TLS error"
        else -> error.javaClass.simpleName
    }
}

/**
 * Records failed requests (network errors and HTTP 4xx/5xx) without changing what the caller sees, and backs
 * off a host for a while after it answers with a 429.
 *
 * A rate limit is per host, not per video/episode: the extension's own retry (a different episode, a
 * different title) hits the exact same host again seconds later and gets 429'd again, since nothing told it
 * the host was still cooling down. This turned "kwik.cx" into a recurring failure across unrelated titles in
 * the same session instead of a one-off. Short-circuiting repeat requests to that host during its backoff
 * window - instead of sending them and getting another 429 - stops that pile-on.
 */
internal class FailureRecordingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        val backoffUntil = rateLimitedUntilMs[host]
        if (backoffUntil != null) {
            if (System.currentTimeMillis() < backoffUntil) {
                NetworkFailureLog.record(host, "HTTP 429 (backing off)")
                throw IOException("$host is rate-limited, not retrying yet")
            }
            rateLimitedUntilMs.remove(host, backoffUntil)
        }
        val response = try {
            chain.proceed(chain.request())
        } catch (error: IOException) {
            NetworkFailureLog.record(host, NetworkFailureLog.describe(error))
            throw error
        }
        if (response.code == 429) {
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
            val backoffMs = ((retryAfterSeconds ?: DEFAULT_BACKOFF_SECONDS) * 1000L)
                .coerceIn(MIN_BACKOFF_MS, MAX_BACKOFF_MS)
            rateLimitedUntilMs[host] = System.currentTimeMillis() + backoffMs
        }
        if (response.code >= 400) NetworkFailureLog.record(host, "HTTP ${response.code}")
        return response
    }

    private companion object {
        val rateLimitedUntilMs = ConcurrentHashMap<String, Long>()
        const val DEFAULT_BACKOFF_SECONDS = 20L
        const val MIN_BACKOFF_MS = 5_000L
        const val MAX_BACKOFF_MS = 5 * 60_000L
    }
}
