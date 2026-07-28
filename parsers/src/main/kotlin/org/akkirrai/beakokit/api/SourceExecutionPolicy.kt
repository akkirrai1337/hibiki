package org.akkirrai.beakokit.api

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
