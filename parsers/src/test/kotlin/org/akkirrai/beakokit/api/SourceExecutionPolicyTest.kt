package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
