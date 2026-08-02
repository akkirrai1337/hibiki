package org.akkirrai.beakokit.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceHostHttpTest {
    @Test
    fun `http client requires network capability`() = runBlocking {
        val client = FakeHttpClient(SourceHostRequirements())

        assertFailsWith<SourceHostCapabilityException> {
            client.execute(SourceHostHttpRequest(method = "GET", url = "https://example.com"))
        }
    }

    @Test
    fun `http client forwards request after capability check`() = runBlocking {
        val client = FakeHttpClient(requirements(SourceHostCapability.NETWORK))
        val request = SourceHostHttpRequest(
            method = "POST",
            url = "https://example.com/search",
            body = "query=test",
            timeoutMillis = 5_000,
            maxResponseBytes = 1024,
        )

        val response = client.execute(request)

        assertEquals(request, client.lastRequest)
        assertEquals(200, response.statusCode)
    }

    @Test
    fun `request limits must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            SourceHostHttpRequest("GET", "https://example.com", timeoutMillis = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            SourceHostHttpRequest("GET", "https://example.com", maxResponseBytes = 0)
        }
    }

    @Test
    fun `http client rejects origins outside manifest policy`() = runBlocking {
        val client = FakeHttpClient(requirements(SourceHostCapability.NETWORK))

        assertFailsWith<IllegalArgumentException> {
            client.execute(SourceHostHttpRequest(method = "GET", url = "https://other.example.com"))
        }
    }

    private class FakeHttpClient(
        override val requirements: SourceHostRequirements,
    ) : SourceHostHttpClient() {
        var lastRequest: SourceHostHttpRequest? = null

        protected override suspend fun executeNetwork(request: SourceHostHttpRequest): SourceHostHttpResponse {
            lastRequest = request
            return SourceHostHttpResponse(statusCode = 200, body = "ok")
        }
    }

    private fun requirements(vararg capabilities: SourceHostCapability) = SourceHostRequirements(
        capabilities = capabilities.toSet(),
        networkPolicy = SourceHostNetworkPolicy(setOf("example.com")),
    )
}
