package org.akkirrai.beakokit.api

import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

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

/** Thread-safe default reporter suitable for application hosts and integration tests. */
class InMemorySourceHealthReporter : SourceHealthReporter {
    private val states = ConcurrentHashMap<SourceId, SourceHealth>()

    override fun health(sourceId: SourceId): SourceHealth = states[sourceId] ?: SourceHealth()

    override fun checkStarted(sourceId: SourceId) {
        states.compute(sourceId) { _, previous ->
            (previous ?: SourceHealth()).copy(checkState = SourceHealthCheckState.CHECKING)
        }
    }

    override fun checkSucceeded(sourceId: SourceId, responseTimeMillis: Long) {
        states[sourceId] = SourceHealth(
            availability = SourceAvailability.AVAILABLE,
            checkState = SourceHealthCheckState.COMPLETED,
            responseTimeMillis = responseTimeMillis.coerceAtLeast(0),
        )
    }

    override fun checkFailed(sourceId: SourceId, responseTimeMillis: Long, error: Throwable) {
        states[sourceId] = SourceHealth(
            availability = SourceAvailability.UNAVAILABLE,
            checkState = SourceHealthCheckState.COMPLETED,
            responseTimeMillis = responseTimeMillis.coerceAtLeast(0),
            lastError = error.toHealthError(),
        )
    }

    override fun checkCancelled(sourceId: SourceId) {
        states.compute(sourceId) { _, previous ->
            (previous ?: SourceHealth()).copy(checkState = SourceHealthCheckState.NOT_CHECKED)
        }
    }
}

/** Records an operation without changing its result or swallowing cancellation. */
suspend inline fun <T> SourceHealthReporter.track(
    sourceId: SourceId,
    crossinline operation: suspend () -> T,
): T {
    checkStarted(sourceId)
    val startedAt = System.nanoTime()
    try {
        return operation().also {
            checkSucceeded(sourceId, elapsedMillis(startedAt))
        }
    } catch (error: CancellationException) {
        checkCancelled(sourceId)
        throw error
    } catch (error: Throwable) {
        checkFailed(sourceId, elapsedMillis(startedAt), error)
        throw error
    }
}

@PublishedApi
internal fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

private fun Throwable.toHealthError(): SourceHealthError {
    val sourceError = this as? SourceException
    val reason = when (sourceError?.kind) {
        SourceErrorKind.NETWORK -> SourceFailureReason.NETWORK
        SourceErrorKind.PARSE -> SourceFailureReason.INVALID_RESPONSE
        SourceErrorKind.AUTH -> SourceFailureReason.ACCESS_DENIED
        SourceErrorKind.NOT_FOUND -> SourceFailureReason.NOT_FOUND
        SourceErrorKind.RATE_LIMITED -> SourceFailureReason.RATE_LIMITED
        SourceErrorKind.UNAVAILABLE -> SourceFailureReason.TEMPORARILY_UNAVAILABLE
        SourceErrorKind.UNKNOWN, null -> SourceFailureReason.UNKNOWN
    }
    return SourceHealthError(
        reason = reason,
        message = message?.takeIf(String::isNotBlank) ?: errorMessage(),
        statusCode = sourceError?.statusCode,
    )
}

private fun Throwable.errorMessage(): String = this::class.simpleName ?: "Unknown source error"
