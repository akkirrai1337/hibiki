package org.akkirrai.beakokit.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceHostHttpTest {
    @Test
    fun `http responses reject line breaks in headers`() {
        assertFailsWith<IllegalArgumentException> {
            SourceHostHttpResponse(
                statusCode = 200,
                headers = mapOf("X-Source" to "ok\r\nInjected: yes"),
                body = "body",
            )
        }
    }
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
            SourceHostHttpRequest(
                "GET",
                "https://example.com",
                timeoutMillis = SourceHostHttpRequest.MAX_TIMEOUT_MILLIS + 1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SourceHostHttpRequest("GET", "https://example.com", maxResponseBytes = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            SourceHostHttpRequest(
                "GET",
                "https://example.com",
                maxResponseBytes = SourceHostHttpRequest.MAX_MAX_RESPONSE_BYTES + 1,
            )
        }
    }

    @Test
    fun `request rejects header and request line injection`() {
        assertFailsWith<IllegalArgumentException> {
            SourceHostHttpRequest("GET\r\nX-Injected: true", "https://example.com")
        }
        assertFailsWith<IllegalArgumentException> {
            SourceHostHttpRequest("GET", "https://example.com\nX-Injected: true")
        }
        assertFailsWith<IllegalArgumentException> {
            SourceHostHttpRequest(
                "GET",
                "https://example.com",
                headers = mapOf("X-Test" to "ok\r\nX-Injected: true"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SourceHostHttpRequest(
                "GET",
                "https://example.com",
                headers = mapOf("X-Test\nInjected" to "ok"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SourceHostHttpRequest("GET /", "https://example.com")
        }
        assertFailsWith<IllegalArgumentException> {
            SourceHostHttpRequest(
                "GET",
                "https://example.com",
                headers = mapOf("X-Test:Injected" to "ok"),
            )
        }
    }

    @Test
    fun `http client rejects origins outside manifest policy`() = runBlocking {
        val client = FakeHttpClient(requirements(SourceHostCapability.NETWORK))

        assertFailsWith<IllegalArgumentException> {
            client.execute(SourceHostHttpRequest(method = "GET", url = "https://other.example.com"))
        }
    }

    @Test
    fun `http client enforces response size limit`() = runBlocking {
        val client = FakeHttpClient(
            requirements(SourceHostCapability.NETWORK),
            responseBody = "12345",
        )

        assertFailsWith<SourceHostHttpResponseException> {
            client.execute(
                SourceHostHttpRequest(
                    method = "GET",
                    url = "https://example.com",
                    maxResponseBytes = 4,
                ),
            )
        }
    }

    private class FakeHttpClient(
        override val requirements: SourceHostRequirements,
        private val responseBody: String = "ok",
    ) : SourceHostHttpClient() {
        var lastRequest: SourceHostHttpRequest? = null

        protected override suspend fun executeNetwork(request: SourceHostHttpRequest): SourceHostHttpResponse {
            lastRequest = request
            return SourceHostHttpResponse(statusCode = 200, body = responseBody)
        }
    }

    private fun requirements(vararg capabilities: SourceHostCapability) = SourceHostRequirements(
        capabilities = capabilities.toSet(),
        networkPolicy = SourceHostNetworkPolicy(setOf("example.com")),
    )
}
