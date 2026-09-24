package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toDuration
import kotlin.time.DurationUnit

@Deprecated("Use the version with kotlin.time APIs instead.")
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
): OkHttpClient.Builder = addInterceptor(RateLimitInterceptor(null, permits, period.toDuration(unit.toDurationUnit())))

fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Duration = 1.seconds,
): OkHttpClient.Builder = addInterceptor(RateLimitInterceptor(null, permits, period))

internal fun TimeUnit.toDurationUnit(): DurationUnit = when (this) {
    TimeUnit.NANOSECONDS -> DurationUnit.NANOSECONDS
    TimeUnit.MICROSECONDS -> DurationUnit.MICROSECONDS
    TimeUnit.MILLISECONDS -> DurationUnit.MILLISECONDS
    TimeUnit.SECONDS -> DurationUnit.SECONDS
    TimeUnit.MINUTES -> DurationUnit.MINUTES
    TimeUnit.HOURS -> DurationUnit.HOURS
    TimeUnit.DAYS -> DurationUnit.DAYS
}

/**
 * Sliding-window limiter: at most [permits] requests per [period], optionally only for one [host].
 * Blocks the calling OkHttp thread instead of failing, like the Aniyomi implementation.
 */
internal class RateLimitInterceptor(
    private val host: String?,
    permits: Int,
    period: Duration,
) : Interceptor {
    private val permits = permits.coerceAtLeast(1)
    private val periodNanos = period.inWholeNanoseconds.coerceAtLeast(1)
    private val timestamps = ArrayDeque<Long>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (host != null && !request.url.host.equals(host, ignoreCase = true)) return chain.proceed(request)
        if (chain.call().isCanceled()) throw IOException("Canceled")
        try {
            awaitPermit()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Rate-limit wait interrupted", error)
        }
        if (chain.call().isCanceled()) throw IOException("Canceled")
        return chain.proceed(request)
    }

    private fun awaitPermit() {
        while (true) {
            val waitNanos = synchronized(timestamps) {
                val now = System.nanoTime()
                while (timestamps.isNotEmpty() && now - timestamps.first() >= periodNanos) timestamps.removeFirst()
                if (timestamps.size < permits) {
                    timestamps.addLast(now)
                    0L
                } else {
                    periodNanos - (now - timestamps.first())
                }
            }
            if (waitNanos <= 0) return
            TimeUnit.NANOSECONDS.sleep(waitNanos)
        }
    }
}
