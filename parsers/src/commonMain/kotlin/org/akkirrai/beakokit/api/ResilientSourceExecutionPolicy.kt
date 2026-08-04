package org.akkirrai.beakokit.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SourceResiliencePolicy(
    /**
     * Optional per-source request spacing. It is disabled by default because source operations
     * such as card-detail enrichment must not be globally serialised. Enable it only for a source
     * whose server has a known request-rate requirement.
     */
    val minimumIntervalMillis: Long = 0,
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
 * Shared source runtime policy with optional per-source request spacing and a transient-failure
 * circuit breaker. It intentionally only gates public source operations; source-specific parsing
 * remains unchanged.
 */
class ResilientSourceExecutionPolicy(
    private val healthReporter: SourceHealthReporter,
    private val policy: SourceResiliencePolicy = SourceResiliencePolicy(),
    private val nowMillis: () -> Long = ::currentWallClockMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) : SourceExecutionPolicy {
    private val gates = MutableStateFlow<Map<SourceId, SourceGate>>(emptyMap())

    override suspend fun <T> execute(
        sourceId: SourceId,
        operation: SourceOperation,
        block: suspend () -> T,
    ): T = healthReporter.track(sourceId) {
        val gate = gate(sourceId)
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
        gates.value[sourceId]?.snapshot() ?: SourceCircuitSnapshot()

    private fun gate(sourceId: SourceId): SourceGate {
        while (true) {
            val current = gates.value
            current[sourceId]?.let { return it }
            val created = SourceGate()
            if (gates.compareAndSet(current, current + (sourceId to created))) return created
        }
    }

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

            val waitMillis = if (lastStartedAt == Long.MIN_VALUE) {
                0L
            } else {
                val nextAllowedAt = if (policy.minimumIntervalMillis > Long.MAX_VALUE - lastStartedAt) {
                    Long.MAX_VALUE
                } else {
                    lastStartedAt + policy.minimumIntervalMillis
                }
                (nextAllowedAt - beforeWait).coerceAtLeast(0)
            }
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
                val now = nowMillis()
                openUntil = if (policy.cooldownMillis > Long.MAX_VALUE - now) {
                    Long.MAX_VALUE
                } else {
                    now + policy.cooldownMillis
                }
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
    is SourceUnavailableException -> true
    is SourceException -> kind in setOf(
        SourceErrorKind.NETWORK,
        SourceErrorKind.RATE_LIMITED,
        SourceErrorKind.UNAVAILABLE,
    )
    else -> isPlatformTransientSourceFailure(this)
}
