package org.akkirrai.beakokit.extension

import kotlinx.coroutines.channels.Channel

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
 */
internal class RhinoRuntimePool(
    size: Int,
    factory: () -> RhinoExtensionRuntime,
) {
    private val available = Channel<RhinoExtensionRuntime>(size).apply {
        repeat(size) { trySend(factory()) }
    }

    suspend fun <T> use(block: (RhinoExtensionRuntime) -> T): T {
        val runtime = available.receive()
        try {
            return block(runtime)
        } finally {
            available.send(runtime)
        }
    }
}
