package org.akkirrai.beakokit.api

/** The observed availability of a source. UNKNOWN means it has not completed a check yet. */
enum class SourceAvailability {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE,
}

/** Lifecycle of the most recent source operation recorded by [SourceHealthReporter]. */
enum class SourceHealthCheckState {
    NOT_CHECKED,
    CHECKING,
    COMPLETED,
}

/** A stable, UI-safe classification of why the last source operation failed. */
enum class SourceFailureReason {
    NETWORK,
    ACCESS_DENIED,
    NOT_FOUND,
    RATE_LIMITED,
    TEMPORARILY_UNAVAILABLE,
    INVALID_RESPONSE,
    UNKNOWN,
}

data class SourceHealthError(
    val reason: SourceFailureReason,
    val message: String,
    val statusCode: Int? = null,
)

/** Immutable latest health snapshot for one source. Times are monotonic durations, not wall-clock timestamps. */
data class SourceHealth(
    val availability: SourceAvailability = SourceAvailability.UNKNOWN,
    val checkState: SourceHealthCheckState = SourceHealthCheckState.NOT_CHECKED,
    val responseTimeMillis: Long? = null,
    val lastError: SourceHealthError? = null,
)

/** Host-facing sink for source health. A source records only its own public operations through it. */
interface SourceHealthReporter {
    fun health(sourceId: SourceId): SourceHealth

    fun checkStarted(sourceId: SourceId)

    fun checkSucceeded(sourceId: SourceId, responseTimeMillis: Long)

    fun checkFailed(sourceId: SourceId, responseTimeMillis: Long, error: Throwable)

    fun checkCancelled(sourceId: SourceId)

    companion object {
        val NONE: SourceHealthReporter = object : SourceHealthReporter {
            override fun health(sourceId: SourceId) = SourceHealth()
            override fun checkStarted(sourceId: SourceId) = Unit
            override fun checkSucceeded(sourceId: SourceId, responseTimeMillis: Long) = Unit
            override fun checkFailed(sourceId: SourceId, responseTimeMillis: Long, error: Throwable) = Unit
            override fun checkCancelled(sourceId: SourceId) = Unit
        }
    }
}
