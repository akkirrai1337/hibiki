package org.akkirrai.beakokit.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** Optional observable view for hosts that render source state reactively. */
interface ObservableSourceHealthReporter : SourceHealthReporter {
    val states: StateFlow<Map<SourceId, SourceHealth>>
}

/** Thread-safe default reporter suitable for application hosts and integration tests. */
class InMemorySourceHealthReporter : ObservableSourceHealthReporter {
    private val healthBySource = ConcurrentHashMap<SourceId, SourceHealth>()
    private val mutableStates = MutableStateFlow<Map<SourceId, SourceHealth>>(emptyMap())

    override val states: StateFlow<Map<SourceId, SourceHealth>> = mutableStates.asStateFlow()

    override fun health(sourceId: SourceId): SourceHealth = healthBySource[sourceId] ?: SourceHealth()

    override fun checkStarted(sourceId: SourceId) {
        healthBySource.compute(sourceId) { _, previous ->
            (previous ?: SourceHealth()).copy(checkState = SourceHealthCheckState.CHECKING)
        }
        publishStates()
    }

    override fun checkSucceeded(sourceId: SourceId, responseTimeMillis: Long) {
        healthBySource[sourceId] = SourceHealth(
            availability = SourceAvailability.AVAILABLE,
            checkState = SourceHealthCheckState.COMPLETED,
            responseTimeMillis = responseTimeMillis.coerceAtLeast(0),
        )
        publishStates()
    }

    override fun checkFailed(sourceId: SourceId, responseTimeMillis: Long, error: Throwable) {
        healthBySource[sourceId] = SourceHealth(
            availability = SourceAvailability.UNAVAILABLE,
            checkState = SourceHealthCheckState.COMPLETED,
            responseTimeMillis = responseTimeMillis.coerceAtLeast(0),
            lastError = error.toHealthError(),
        )
        publishStates()
    }

    override fun checkCancelled(sourceId: SourceId) {
        healthBySource.compute(sourceId) { _, previous ->
            (previous ?: SourceHealth()).copy(checkState = SourceHealthCheckState.NOT_CHECKED)
        }
        publishStates()
    }

    private fun publishStates() {
        mutableStates.value = healthBySource.toMap()
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
