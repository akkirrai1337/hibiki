package org.akkirrai.beakokit.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class BeakoKitHttpDefaultsTest {
    @Test
    fun `retries temporary failures for safe requests and keeps the shared user agent`() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine { request ->
            requestCount += 1
            assertEquals("TestKit/1.0", request.headers[HttpHeaders.UserAgent])
            if (requestCount == 1) {
                respond("busy", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "0"))
            } else {
                respond("ok")
            }
        }) {
            installBeakoKitHttpDefaults(BeakoKitHttpPolicy(userAgent = "TestKit/1.0"))
        }

        try {
            assertEquals(HttpStatusCode.OK, client.get("https://source.test").status)
            assertEquals(2, requestCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun `does not retry unsafe requests`() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine {
            requestCount += 1
            respond("failure", HttpStatusCode.InternalServerError)
        }) {
            installBeakoKitHttpDefaults()
        }

        try {
            assertEquals(HttpStatusCode.InternalServerError, client.post("https://source.test").status)
            assertEquals(1, requestCount)
        } finally {
            client.close()
        }
    }
}
