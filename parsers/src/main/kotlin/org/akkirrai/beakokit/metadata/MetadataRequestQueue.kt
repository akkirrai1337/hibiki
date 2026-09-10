package org.akkirrai.beakokit.metadata

import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One serialized, paced request queue per metadata provider.
 *
 * Every provider here rate-limits by IP, and a screen describing twelve titles at once would
 * otherwise open twelve sockets and collect a 429 for most of them. Requests are chained rather than
 * fired in parallel and spaced by the provider's own interval.
 *
 * A 429 stands the provider down instead of sleeping in line: waiting out a Retry-After inside the
 * queue held every request behind it for up to a minute, so one throttled lookup froze a whole
 * screen's worth. Failing fast costs that lookup its description - the source's own still shows -
 * and lets the rest fail fast too instead of queueing up to be throttled in turn.
 */
class MetadataRequestQueue(
    private val minIntervalMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()
    private var lastRequestAtMillis = 0L
    private var cooledUntilMillis = 0L

    /** Null for every failure - offline, a non-2xx status, a provider being stood down. Callers
     * treat that as "no metadata this time" and keep the source's own, so nothing here throws. */
    suspend fun <T> run(request: suspend () -> HttpResponse, parse: suspend (HttpResponse) -> T?): T? = lock.withLock {
        if (nowMillis() < cooledUntilMillis) return@withLock null
        val wait = lastRequestAtMillis + minIntervalMillis - nowMillis()
        if (wait > 0) delay(wait)
        lastRequestAtMillis = nowMillis()
        val response = runCatching { request() }.getOrNull() ?: return@withLock null
        if (response.status.value == 429) {
            val retryAfter = response.headers["Retry-After"]?.toLongOrNull()
            cooledUntilMillis = nowMillis() + (retryAfter?.times(1_000) ?: DEFAULT_RATE_LIMIT_DELAY_MILLIS)
            return@withLock null
        }
        if (response.status.value !in 200..299) return@withLock null
        runCatching { parse(response) }.getOrNull()
    }

    private companion object {
        // Both APIs answer 429 without a Retry-After often enough to need a floor, and both count
        // per minute, so a minute is the honest wait.
        const val DEFAULT_RATE_LIMIT_DELAY_MILLIS = 60_000L
    }
}
