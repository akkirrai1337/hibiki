package org.akkirrai.beakokit.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceHealthTest {
    private val sourceId = SourceId("health-test")

    @Test
    fun `successful operation marks source as available`() = runBlocking {
        val reporter = InMemorySourceHealthReporter()

        val result = reporter.track(sourceId) { "ok" }

        assertEquals("ok", result)
        assertEquals(SourceAvailability.AVAILABLE, reporter.health(sourceId).availability)
        assertEquals(SourceHealthCheckState.COMPLETED, reporter.health(sourceId).checkState)
        assertNull(reporter.health(sourceId).lastError)
        assertTrue(reporter.health(sourceId).responseTimeMillis!! >= 0)
    }

    @Test
    fun `source exception preserves readable failure reason and status`() = runBlocking {
        val reporter = InMemorySourceHealthReporter()

        assertFailsWith<SourceException> {
            reporter.track(sourceId) {
                throw SourceException(
                    message = "Request limit reached",
                    statusCode = 429,
                    kind = SourceErrorKind.RATE_LIMITED,
                )
            }
        }

        val health = reporter.health(sourceId)
        assertEquals(SourceAvailability.UNAVAILABLE, health.availability)
        assertEquals(SourceHealthCheckState.COMPLETED, health.checkState)
        assertEquals(SourceFailureReason.RATE_LIMITED, health.lastError?.reason)
        assertEquals(429, health.lastError?.statusCode)
        assertEquals("Request limit reached", health.lastError?.message)
    }

    @Test
    fun `cancellation is propagated and does not become a source failure`() = runBlocking {
        val reporter = InMemorySourceHealthReporter()

        assertFailsWith<CancellationException> {
            reporter.track(sourceId) { throw CancellationException("cancelled") }
        }

        val health = reporter.health(sourceId)
        assertEquals(SourceAvailability.UNKNOWN, health.availability)
        assertEquals(SourceHealthCheckState.NOT_CHECKED, health.checkState)
        assertNull(health.lastError)
    }

    @Test
    fun `observable states publish completed source health`() = runBlocking {
        val reporter = InMemorySourceHealthReporter()

        reporter.track(sourceId) { "ok" }

        assertEquals(SourceAvailability.AVAILABLE, reporter.states.value[sourceId]?.availability)
    }
}
