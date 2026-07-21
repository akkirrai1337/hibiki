package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceExecutionPolicyTest {
    private val sourceId = SourceId("execution-test")

    @Test
    fun `default context executes source operations through health tracking policy`() = runBlocking {
        val reporter = InMemorySourceHealthReporter()
        val client = HttpClient(MockEngine { error("Network must not be called") })
        try {
            val context = DefaultSourceContext(
                httpClient = client,
                preferredLanguages = listOf(SourceLanguage.ENGLISH),
                sourceHealthReporter = reporter,
            )

            val result = context.sourceExecutionPolicy.execute(sourceId, SourceOperation.SEARCH) { "ok" }

            assertEquals("ok", result)
            assertEquals(SourceAvailability.AVAILABLE, reporter.health(sourceId).availability)
        } finally {
            client.close()
        }
    }

    @Test
    fun `custom policy receives operation metadata without changing result`() = runBlocking {
        var capturedSourceId: SourceId? = null
        var capturedOperation: SourceOperation? = null
        val policy = object : SourceExecutionPolicy {
            override suspend fun <T> execute(
                sourceId: SourceId,
                operation: SourceOperation,
                block: suspend () -> T,
            ): T {
                capturedSourceId = sourceId
                capturedOperation = operation
                return block()
            }
        }

        val result = policy.execute(sourceId, SourceOperation.PLAYER_LINKS) { 42 }

        assertEquals(42, result)
        assertEquals(sourceId, capturedSourceId)
        assertEquals(SourceOperation.PLAYER_LINKS, capturedOperation)
    }

    @Test
    fun `transient failures open a circuit and a later recovery probe closes it`() = runBlocking {
        var now = 0L
        var attempts = 0
        val policy = ResilientSourceExecutionPolicy(
            healthReporter = InMemorySourceHealthReporter(),
            policy = SourceResiliencePolicy(minimumIntervalMillis = 0, failureThreshold = 2, cooldownMillis = 100),
            nowMillis = { now },
        )

        repeat(2) {
            assertFailsWith<SourceException> {
                policy.execute(sourceId, SourceOperation.SEARCH) {
                    attempts += 1
                    throw SourceException("temporary", kind = SourceErrorKind.NETWORK)
                }
            }
        }
        assertEquals(SourceCircuitState.OPEN, policy.circuit(sourceId).state)
        assertFailsWith<SourceCircuitOpenException> {
            policy.execute(sourceId, SourceOperation.SEARCH) { attempts += 1 }
        }
        assertEquals(2, attempts)

        now = 100
        assertEquals("recovered", policy.execute(sourceId, SourceOperation.SEARCH) {
            attempts += 1
            "recovered"
        })
        assertEquals(SourceCircuitState.CLOSED, policy.circuit(sourceId).state)
        assertEquals(3, attempts)
    }

    @Test
    fun `policy spaces operations for the same source`() = runBlocking {
        var now = 0L
        val waits = mutableListOf<Long>()
        val policy = ResilientSourceExecutionPolicy(
            healthReporter = InMemorySourceHealthReporter(),
            policy = SourceResiliencePolicy(minimumIntervalMillis = 50),
            nowMillis = { now },
            wait = { duration ->
                waits += duration
                now += duration
            },
        )

        policy.execute(sourceId, SourceOperation.SEARCH) { Unit }
        policy.execute(sourceId, SourceOperation.DETAILS) { Unit }

        assertEquals(listOf(50L), waits)
    }

    @Test
    fun `default policy does not serialize source operations`() = runBlocking {
        val waits = mutableListOf<Long>()
        val policy = ResilientSourceExecutionPolicy(
            healthReporter = InMemorySourceHealthReporter(),
            nowMillis = { 1_000L },
            wait = { duration -> waits += duration },
        )

        policy.execute(sourceId, SourceOperation.SEARCH) { Unit }
        policy.execute(sourceId, SourceOperation.DETAILS) { Unit }

        assertEquals(emptyList(), waits)
    }

    @Test
    fun `first operation is not delayed by the request interval`() = runBlocking {
        val waits = mutableListOf<Long>()
        val policy = ResilientSourceExecutionPolicy(
            healthReporter = InMemorySourceHealthReporter(),
            policy = SourceResiliencePolicy(minimumIntervalMillis = 250),
            nowMillis = { 1_000L },
            wait = { duration -> waits += duration },
        )

        assertEquals("loaded", policy.execute(sourceId, SourceOperation.SEARCH) { "loaded" })

        assertEquals(emptyList(), waits)
    }
}
