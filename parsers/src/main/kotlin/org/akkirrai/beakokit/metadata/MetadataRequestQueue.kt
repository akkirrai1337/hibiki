package org.akkirrai.beakokit.metadata

import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One paced request queue per metadata provider.
 *
 * Every provider here rate-limits by IP, and a screen describing twelve titles at once would
 * otherwise open twelve sockets at once and collect a 429 for most of them. Requests are spaced by
 * the provider's own interval - but only their *start* time is serialized here. The interval alone
 * is what a rate limit actually counts, so once a request has been let through, its round trip and
 * parsing run outside the lock and can overlap with the next one's. Holding the lock across the full
 * request used to mean a single slow response held up every other title waiting on this provider for
 * its entire latency, not just the pacing interval - on a slow connection that turned a page of
 * twenty cards into twenty sequential round trips instead of twenty paced-but-overlapping ones.
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
    suspend fun <T> run(request: suspend () -> HttpResponse, parse: suspend (HttpResponse) -> T?): T? {
        val admitted = lock.withLock {
            if (nowMillis() < cooledUntilMillis) return null
            val wait = lastRequestAtMillis + minIntervalMillis - nowMillis()
            if (wait > 0) delay(wait)
            lastRequestAtMillis = nowMillis()
            true
        }
        if (!admitted) return null

        val response = runCatching { request() }.getOrNull() ?: return null
        if (response.status.value == 429) {
            val retryAfter = response.headers["Retry-After"]?.toLongOrNull()
            lock.withLock {
                cooledUntilMillis = nowMillis() + (retryAfter?.times(1_000) ?: DEFAULT_RATE_LIMIT_DELAY_MILLIS)
            }
            return null
        }
        if (response.status.value !in 200..299) return null
        return runCatching { parse(response) }.getOrNull()
    }

    private companion object {
        // Both APIs answer 429 without a Retry-After often enough to need a floor, and both count
        // per minute, so a minute is the honest wait.
        const val DEFAULT_RATE_LIMIT_DELAY_MILLIS = 60_000L
    }
}
