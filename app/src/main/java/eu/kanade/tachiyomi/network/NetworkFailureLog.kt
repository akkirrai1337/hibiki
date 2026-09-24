package eu.kanade.tachiyomi.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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

/** Records failed requests (network errors and HTTP 4xx/5xx) without changing what the caller sees. */
internal class FailureRecordingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        val response = try {
            chain.proceed(chain.request())
        } catch (error: IOException) {
            NetworkFailureLog.record(host, NetworkFailureLog.describe(error))
            throw error
        }
        if (response.code >= 400) NetworkFailureLog.record(host, "HTTP ${response.code}")
        return response
    }
}
