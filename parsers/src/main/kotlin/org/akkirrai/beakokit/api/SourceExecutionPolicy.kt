package org.akkirrai.beakokit.api

/** Public source operations governed by the shared runtime policy. */
enum class SourceOperation {
    SEARCH,
    FILTER_CATALOG,
    DETAILS,
    LATEST,
    SCHEDULE,
    PLAYBACK_GROUPS,
    PLAYER_LINKS,
}

/**
 * Central execution boundary for source operations.
 *
 * Policies may add health reporting, throttling, caching, circuit breaking or tracing without
 * pushing those cross-cutting concerns back into individual sources.
 */
interface SourceExecutionPolicy {
    suspend fun <T> execute(
        sourceId: SourceId,
        operation: SourceOperation,
        block: suspend () -> T,
    ): T

    companion object {
        val NONE: SourceExecutionPolicy = object : SourceExecutionPolicy {
            override suspend fun <T> execute(
                sourceId: SourceId,
                operation: SourceOperation,
                block: suspend () -> T,
            ): T = block()
        }
    }
}

/** Default runtime policy that keeps SourceHealth in sync while preserving operation semantics. */
class HealthTrackingSourceExecutionPolicy(
    private val healthReporter: SourceHealthReporter,
) : SourceExecutionPolicy {
    override suspend fun <T> execute(
        sourceId: SourceId,
        operation: SourceOperation,
        block: suspend () -> T,
    ): T = healthReporter.track(sourceId, block)
}
