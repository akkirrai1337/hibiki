package org.akkirrai.beakokit.metadata

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/** What one key got out of a batched request. */
internal sealed interface BatchOutcome<out V> {
    /** The request itself failed, or was never sent - the same "null means failure" as a client call. */
    data object Failed : BatchOutcome<Nothing>

    /** The request answered; [value] is null when it had nothing for this key. */
    data class Done<V>(val value: V?) : BatchOutcome<V>
}

/**
 * Folds concurrent lookups into as few requests as a provider allows.
 *
 * There is no time window to tune: one request goes out at a time, and the keys it carries are taken
 * only once the provider's queue has admitted it (see [execute]'s `takeKeys`). Everything that piled
 * up while it waited for its slot rides along. Idle, a lone lookup leaves at once, exactly as before;
 * under load - a page of cards against a queue paced at one request every two seconds - a whole page
 * shares one request instead of each waiting two seconds behind the last.
 *
 * [execute] must call `takeKeys` exactly once, when the request is being built, and return what it
 * found by key, or null when the request failed. Not calling it means nothing was sent (the provider
 * is stood down), and every key waiting fails.
 */
internal class RequestBatcher<K, V : Any>(
    private val maxBatch: Int,
    priority: MetadataPriority,
    private val execute: suspend (takeKeys: () -> List<K>) -> Map<K, V>?,
) {
    private class Waiter<K, V>(val key: K) {
        val result = CompletableDeferred<BatchOutcome<V>>()
    }

    private val lock = Any()
    private val pending = ArrayDeque<Waiter<K, V>>()
    private var draining = false

    // Requests run here rather than in the first caller's coroutine, so a caller cancelled mid-flight
    // cannot take everyone else's answer down with it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + priority)

    suspend fun load(key: K): BatchOutcome<V> {
        val waiter = Waiter<K, V>(key)
        val startDrain = synchronized(lock) {
            pending.addLast(waiter)
            val idle = !draining
            draining = true
            idle
        }
        if (startDrain) scope.launch { drain() }
        return waiter.result.await()
    }

    private suspend fun drain() {
        while (true) {
            var batch: List<Waiter<K, V>> = emptyList()
            val results = runCatching {
                execute {
                    batch = synchronized(lock) { take(maxBatch) }
                    batch.map { it.key }.distinct()
                }
            }.getOrNull()
            if (batch.isEmpty()) batch = synchronized(lock) { take(Int.MAX_VALUE) }
            for (waiter in batch) {
                waiter.result.complete(if (results == null) BatchOutcome.Failed else BatchOutcome.Done(results[waiter.key]))
            }
            synchronized(lock) {
                if (pending.isEmpty()) {
                    draining = false
                    return
                }
            }
        }
    }

    private fun take(max: Int): List<Waiter<K, V>> {
        val taken = ArrayList<Waiter<K, V>>(minOf(max, pending.size))
        while (taken.size < max && pending.isNotEmpty()) taken += pending.removeFirst()
        return taken
    }
}

/** A [RequestBatcher] per [MetadataPriority] lane, so a foreground lookup never rides in (and waits
 * behind) a background batch. */
internal class BatchLanes<K, V : Any>(
    maxBatch: Int,
    execute: suspend (takeKeys: () -> List<K>) -> Map<K, V>?,
) {
    private val foreground = RequestBatcher(maxBatch, MetadataPriority.Foreground, execute)
    private val background = RequestBatcher(maxBatch, MetadataPriority.Background, execute)

    suspend fun load(key: K): BatchOutcome<V> {
        val lane = if (currentCoroutineContext()[MetadataPriority]?.background == true) background else foreground
        return lane.load(key)
    }
}
