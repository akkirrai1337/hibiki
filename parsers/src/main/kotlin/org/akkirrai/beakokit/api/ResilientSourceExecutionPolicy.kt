package org.akkirrai.beakokit.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class SourceResiliencePolicy(
    val minimumIntervalMillis: Long = 250,
    val failureThreshold: Int = 3,
    val cooldownMillis: Long = 30_000,
) {
    init {
        require(minimumIntervalMillis >= 0) { "Minimum source request interval must not be negative" }
        require(failureThreshold > 0) { "Source failure threshold must be positive" }
        require(cooldownMillis > 0) { "Source cooldown must be positive" }
    }
}

enum class SourceCircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN,
}

data class SourceCircuitSnapshot(
    val state: SourceCircuitState = SourceCircuitState.CLOSED,
    val consecutiveFailures: Int = 0,
    val retryAfterMillis: Long = 0,
)

/** Thrown before network I/O when a source is cooling down or a recovery probe is already in flight. */
class SourceCircuitOpenException(
    val sourceId: SourceId,
    val retryAfterMillis: Long,
    val recoveryProbeInFlight: Boolean,
) : SourceUnavailableException(
    message = if (recoveryProbeInFlight) {
        "${sourceId.value} is checking recovery after temporary failures"
    } else {
        "${sourceId.value} is temporarily unavailable; retry in ${retryAfterMillis.coerceAtLeast(1)} ms"
    },
)

/**
 * Shared source runtime policy with per-source request spacing and a transient-failure circuit breaker.
 * It intentionally only gates public source operations; source-specific parsing remains unchanged.
 */
class ResilientSourceExecutionPolicy(
    private val healthReporter: SourceHealthReporter,
    private val policy: SourceResiliencePolicy = SourceResiliencePolicy(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) : SourceExecutionPolicy {
    private val gates = ConcurrentHashMap<SourceId, SourceGate>()

    override suspend fun <T> execute(
        sourceId: SourceId,
        operation: SourceOperation,
        block: suspend () -> T,
    ): T = healthReporter.track(sourceId) {
        val gate = gates.computeIfAbsent(sourceId) { SourceGate() }
        gate.acquire(sourceId)
        try {
            block().also { gate.recordSuccess() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            gate.recordFailure(error)
            throw error
        }
    }

    fun circuit(sourceId: SourceId): SourceCircuitSnapshot =
        gates[sourceId]?.snapshot() ?: SourceCircuitSnapshot()

    private inner class SourceGate {
        private val mutex = Mutex()
        private var lastStartedAt = Long.MIN_VALUE
        private var consecutiveFailures = 0
        private var openUntil = 0L
        private var recoveryProbeInFlight = false

        suspend fun acquire(sourceId: SourceId) = mutex.withLock {
            val beforeWait = nowMillis()
            if (openUntil > beforeWait) {
                throw SourceCircuitOpenException(sourceId, openUntil - beforeWait, recoveryProbeInFlight = false)
            }
            if (openUntil != 0L) {
                if (recoveryProbeInFlight) {
                    throw SourceCircuitOpenException(sourceId, 0, recoveryProbeInFlight = true)
                }
                recoveryProbeInFlight = true
            }

            val waitMillis = (lastStartedAt + policy.minimumIntervalMillis - beforeWait).coerceAtLeast(0)
            if (waitMillis > 0) wait(waitMillis)
            lastStartedAt = nowMillis()
        }

        suspend fun recordSuccess() = mutex.withLock {
            consecutiveFailures = 0
            openUntil = 0L
            recoveryProbeInFlight = false
        }

        suspend fun recordFailure(error: Throwable) = mutex.withLock {
            recoveryProbeInFlight = false
            if (!error.isTransientSourceFailure()) {
                consecutiveFailures = 0
                return@withLock
            }
            consecutiveFailures += 1
            if (consecutiveFailures >= policy.failureThreshold) {
                openUntil = nowMillis() + policy.cooldownMillis
            }
        }

        fun snapshot(): SourceCircuitSnapshot {
            val now = nowMillis()
            val state = when {
                openUntil > now -> SourceCircuitState.OPEN
                openUntil != 0L -> SourceCircuitState.HALF_OPEN
                else -> SourceCircuitState.CLOSED
            }
            return SourceCircuitSnapshot(
                state = state,
                consecutiveFailures = consecutiveFailures,
                retryAfterMillis = (openUntil - now).coerceAtLeast(0),
            )
        }
    }
}

private fun Throwable.isTransientSourceFailure(): Boolean = when (this) {
    is IOException, is SourceUnavailableException -> true
    is SourceException -> kind in setOf(
        SourceErrorKind.NETWORK,
        SourceErrorKind.RATE_LIMITED,
        SourceErrorKind.UNAVAILABLE,
    )
    else -> false
}
