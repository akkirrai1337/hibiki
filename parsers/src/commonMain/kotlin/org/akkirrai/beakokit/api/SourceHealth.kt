package org.akkirrai.beakokit.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
{
    companion object {
        const val MAX_MESSAGE_LENGTH: Int = 4 * 1024
    }
}

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
class InMemorySourceHealthReporter : ObservableSourceHealthReporter {
    private val mutableStates = MutableStateFlow<Map<SourceId, SourceHealth>>(emptyMap())

    override val states: StateFlow<Map<SourceId, SourceHealth>> = mutableStates.asStateFlow()

    override fun health(sourceId: SourceId): SourceHealth = states.value[sourceId] ?: SourceHealth()

    override fun checkStarted(sourceId: SourceId) {
        mutableStates.update { states ->
            states + (sourceId to (states[sourceId] ?: SourceHealth()).copy(checkState = SourceHealthCheckState.CHECKING))
        }
    }

    override fun checkSucceeded(sourceId: SourceId, responseTimeMillis: Long) {
        mutableStates.update { states ->
            states + (sourceId to SourceHealth(
                availability = SourceAvailability.AVAILABLE,
                checkState = SourceHealthCheckState.COMPLETED,
                responseTimeMillis = responseTimeMillis.coerceAtLeast(0),
            ))
        }
    }

    override fun checkFailed(sourceId: SourceId, responseTimeMillis: Long, error: Throwable) {
        mutableStates.update { states ->
            states + (sourceId to SourceHealth(
                availability = SourceAvailability.UNAVAILABLE,
                checkState = SourceHealthCheckState.COMPLETED,
                responseTimeMillis = responseTimeMillis.coerceAtLeast(0),
                lastError = error.toHealthError(),
            ))
        }
    }

    override fun checkCancelled(sourceId: SourceId) {
        mutableStates.update { states ->
            states + (sourceId to (states[sourceId] ?: SourceHealth()).copy(checkState = SourceHealthCheckState.NOT_CHECKED))
        }
    }
}

/** Records an operation without changing its result or swallowing cancellation. */
suspend inline fun <T> SourceHealthReporter.track(
    sourceId: SourceId,
    crossinline operation: suspend () -> T,
): T {
    checkStarted(sourceId)
    val startedAt = monotonicTimeMillis()
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
internal fun elapsedMillis(startedAt: Long): Long = (monotonicTimeMillis() - startedAt).coerceAtLeast(0)

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
        message = (message?.takeIf(String::isNotBlank) ?: errorMessage()).toHealthMessage(),
        statusCode = sourceError?.statusCode,
    )
}

private fun Throwable.errorMessage(): String = this::class.simpleName ?: "Unknown source error"

private fun String.toHealthMessage(): String = replace('\r', ' ')
    .replace('\n', ' ')
    .take(SourceHealthError.MAX_MESSAGE_LENGTH)
