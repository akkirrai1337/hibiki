package org.akkirrai.beakokit.metadata

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap

/** Shares one in-progress request by key. Unlike [RequestBatcher], this is for endpoints that cannot
 * carry several keys in one HTTP request (AniList lookups, for example). */
internal class RequestCoalescer<K, V> {
    private val inFlight = ConcurrentHashMap<K, CompletableDeferred<V>>()

    suspend fun load(key: K, request: suspend () -> V): V {
        while (true) {
            val mine = CompletableDeferred<V>()
            val existing = inFlight.putIfAbsent(key, mine)
            if (existing == null) {
                try {
                    return request().also(mine::complete)
                } catch (error: Throwable) {
                    mine.completeExceptionally(error)
                    throw error
                } finally {
                    inFlight.remove(key, mine)
                }
            }
            try {
                return existing.await()
            } catch (error: CancellationException) {
                // The owner may have been cancelled by a screen going away. A surviving caller
                // retries and becomes the next owner instead of receiving a stale cancellation.
                currentCoroutineContext().ensureActive()
            }
        }
    }
}
