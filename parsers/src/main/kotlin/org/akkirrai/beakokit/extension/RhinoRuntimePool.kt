package org.akkirrai.beakokit.extension

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger

/**
 * A small pool of independent [RhinoExtensionRuntime] instances for one extension.
 *
 * Each Rhino `Scriptable` scope is single-threaded and stateful (module-level `var`s like a
 * source's session cache or its catalog-summary cache), so a single runtime can only run one
 * script call at a time - see [RhinoExtensionRuntime.callRaw]. This pool lets up to [size] calls
 * run genuinely concurrently by giving each one its own runtime with its own scope, instead of
 * every call queueing on one shared lock.
 *
 * The trade-off is that per-runtime script state isn't shared between pool members - e.g. an
 * AnimePahe extension's cached Cloudflare session is earned independently by whichever runtime
 * first needs it, and a catalog-summary cache populated on one runtime won't backfill a details
 * fetch that happens to land on another. Both are graceful degradations (one extra request the
 * first time a given runtime is used; a slightly less complete merge), not correctness bugs -
 * see the `challenge()`/`cachedSession` and `summaries` comments in the individual extensions.
 *
 * Members are built on demand rather than all at once. Standing one up means a fresh set of Rhino
 * standard objects, a fresh set of globals and one execution of the payload, so building the whole
 * pool up front put all of that in front of the very first call to a source - for runtimes a
 * sequential search or details fetch never touches. A second one is built only when a second call
 * is genuinely in flight at the same time.
 */
internal class RhinoRuntimePool(
    private val size: Int,
    private val factory: () -> RhinoExtensionRuntime,
) {
    private val available = Channel<RhinoExtensionRuntime>(size)
    private val created = AtomicInteger(0)

    /** Claims a free runtime, else builds one while the pool is not full, else waits for one. */
    private suspend fun acquire(): RhinoExtensionRuntime {
        available.tryReceive().getOrNull()?.let { return it }
        while (true) {
            val current = created.get()
            if (current >= size) return available.receive()
            if (!created.compareAndSet(current, current + 1)) continue
            return runCatching(factory).getOrElse { error ->
                // The slot was reserved before the runtime existed; a payload that fails to load
                // must not shrink the pool's capacity permanently.
                created.decrementAndGet()
                throw error
            }
        }
    }

    suspend fun <T> use(block: (RhinoExtensionRuntime) -> T): T {
        val runtime = acquire()
        try {
            return block(runtime)
        } finally {
            available.send(runtime)
        }
    }
}
