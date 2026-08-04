package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class KtorSourceRepositoryTransportTest {
    @Test
    fun `transport maps Ktor response to repository response`() = runBlocking {
        val transport = KtorSourceRepositoryTransport(
            HttpClient(MockEngine {
                respond(
                    content = "{\"apiVersion\":1}",
                    status = HttpStatusCode.Accepted,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }),
        )

        val response = transport.get(
            url = "https://example.com/repository.json",
            limits = SourceRepositoryLoadLimits(),
        )

        assertEquals(HttpStatusCode.Accepted.value, response.statusCode)
        assertEquals("{\"apiVersion\":1}", response.body)
    }

    @Test
    fun `transport rejects an oversized response before decoding`() = runBlocking {
        val transport = KtorSourceRepositoryTransport(
            HttpClient(MockEngine { respond("12345") }),
        )

        val error = assertFailsWith<SourceRepositoryLoadException> {
            transport.get(
                url = "https://example.com/repository.json",
                limits = SourceRepositoryLoadLimits(maxResponseBytes = 4),
            )
        }

        assertEquals(SourceErrorKind.UNAVAILABLE, error.kind)
    }

    @Test
    fun `transport enforces response limit on raw bytes`() = runBlocking {
        val transport = KtorSourceRepositoryTransport(
            HttpClient(MockEngine { respond(byteArrayOf(0xC3.toByte(), 0xA9.toByte(), 0xC3.toByte())) }),
        )

        val error = assertFailsWith<SourceRepositoryLoadException> {
            transport.get(
                url = "https://example.com/repository.json",
                limits = SourceRepositoryLoadLimits(maxResponseBytes = 2),
            )
        }

        assertEquals(SourceErrorKind.UNAVAILABLE, error.kind)
    }

    @Test
    fun `transport returns an error status without reading its body`() = runBlocking {
        val transport = KtorSourceRepositoryTransport(
            HttpClient(MockEngine {
                respond(
                    content = "large error body that must be ignored",
                    status = HttpStatusCode.ServiceUnavailable,
                )
            }),
        )

        val response = transport.get(
            url = "https://example.com/repository.json",
            limits = SourceRepositoryLoadLimits(maxResponseBytes = 1),
        )

        assertEquals(HttpStatusCode.ServiceUnavailable.value, response.statusCode)
        assertEquals("", response.body)
    }

    @Test
    fun `repository limits reserve space for the size sentinel`() {
        assertFailsWith<IllegalArgumentException> {
            SourceRepositoryLoadLimits(maxResponseBytes = Long.MAX_VALUE)
        }
    }
}
