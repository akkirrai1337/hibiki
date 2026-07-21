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

    /** Optional safe-result cache hook. Callers must not use it for personalised or expiring URLs. */
    suspend fun <T> execute(
        sourceId: SourceId,
        operation: SourceOperation,
        cacheKey: String,
        cacheTtlMillis: Long,
        block: suspend () -> T,
    ): T = execute(sourceId, operation, block)

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
