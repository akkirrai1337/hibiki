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
}
