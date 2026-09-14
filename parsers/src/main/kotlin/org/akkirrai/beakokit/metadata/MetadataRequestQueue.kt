package org.akkirrai.beakokit.metadata

import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

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
 * Pacing is a token bucket (GCRA): up to [burst] requests start back to back after a quiet spell,
 * and only then does the interval apply. Every provider here counts a window, not the gap between
 * two requests, so a screen's first handful of lookups - the ones a user is actually waiting on - no
 * longer pay the interval each.
 *
 * A 429 stands the provider down instead of sleeping in line: waiting out a Retry-After inside the
 * queue held every request behind it for up to a minute, so one throttled lookup froze a whole
 * screen's worth. Failing fast costs that lookup its description - the source's own still shows -
 * and lets the rest fail fast too instead of queueing up to be throttled in turn.
 */
class MetadataRequestQueue(
    private val minIntervalMillis: Long,
    private val burst: Int = 1,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()
    private val burstToleranceMillis = minIntervalMillis * (burst - 1).coerceAtLeast(0)
    @Volatile private var theoreticalArrivalMillis = 0L
    @Volatile private var cooledUntilMillis = 0L
    private val foregroundWaiting = MutableStateFlow(0)
    private val waiting = AtomicInteger(0)

    /** Null for every failure - offline, a non-2xx status, a provider being stood down. Callers
     * treat that as "no metadata this time" and keep the source's own, so nothing here throws. */
    suspend fun <T> run(request: suspend () -> HttpResponse, parse: suspend (HttpResponse) -> T?): T? {
        val background = currentCoroutineContext()[MetadataPriority]?.background == true
        if (!admit(background)) return null

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

    /**
     * Roughly how long a request queued now would wait to start - what lets a lookup go to whichever
     * provider frees up first. [Long.MAX_VALUE] while the provider is stood down.
     */
    fun estimatedWaitMillis(): Long {
        val now = nowMillis()
        if (now < cooledUntilMillis) return Long.MAX_VALUE
        val slot = (theoreticalArrivalMillis - burstToleranceMillis - now).coerceAtLeast(0)
        return slot + waiting.get() * minIntervalMillis
    }

    /**
     * Waits for this request's paced start slot. False when the provider is stood down.
     *
     * Two lanes (see [MetadataPriority]): a background request only takes the lock while no
     * foreground request is waiting, and re-checks once it has it, since one may have arrived in
     * between. A foreground request therefore waits at most one pacing interval, however long the
     * background backlog is.
     */
    private suspend fun admit(background: Boolean): Boolean {
        waiting.incrementAndGet()
        if (!background) foregroundWaiting.update { it + 1 }
        try {
            while (true) {
                if (background) foregroundWaiting.first { it == 0 }
                val admitted: Boolean? = lock.withLock {
                    if (background && foregroundWaiting.value > 0) return@withLock null
                    if (nowMillis() < cooledUntilMillis) return@withLock false
                    val now = nowMillis()
                    val arrival = maxOf(theoreticalArrivalMillis, now)
                    val wait = arrival - burstToleranceMillis - now
                    if (wait > 0) delay(wait)
                    theoreticalArrivalMillis = arrival + minIntervalMillis
                    true
                }
                if (admitted != null) return admitted
            }
        } finally {
            if (!background) foregroundWaiting.update { it - 1 }
            waiting.decrementAndGet()
        }
    }

    private companion object {
        // Both APIs answer 429 without a Retry-After often enough to need a floor, and both count
        // per minute, so a minute is the honest wait.
        const val DEFAULT_RATE_LIMIT_DELAY_MILLIS = 60_000L
    }
}
