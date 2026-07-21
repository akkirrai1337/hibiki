package org.akkirrai.beakokit.api

/**
 * Optional source contract for a small, read-only availability check.
 *
 * A successful call means the source's own public endpoint is currently usable. Implementations
 * must route the request through [SourceExecutionPolicy] with [SourceOperation.HEALTH_CHECK], so
 * SourceHealth, throttling and circuit breaking observe it like every other source operation.
 */
interface HealthCheckSource : AnimeSource {
    suspend fun checkHealth()
}
