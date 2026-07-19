package org.akkirrai.beakokit.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.ChallengeSession
import org.akkirrai.beakokit.api.ChallengeSessionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChallengeRequestExecutorTest {
    @Test
    fun `challenge retries with browser cookies and matching user agent`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            requests += request
            if (requests.size == 1) {
                respond(
                    content = "challenge",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf("cf-mitigated", "challenge"),
                )
            } else {
                respond("catalog")
            }
        })
        val provider = ChallengeSessionProvider {
            ChallengeSession(mapOf("cf_clearance" to "clear"), "WebView agent")
        }

        try {
            val response = ChallengeRequestExecutor(client, provider).execute(
                url = "https://source.test/catalog",
                requiredCookieNames = setOf("cf_clearance"),
            )

            assertEquals("catalog", response.bodyAsText())
            assertEquals(2, requests.size)
            assertFalse(requests.first().headers.contains(HttpHeaders.Cookie))
            assertEquals("cf_clearance=clear", requests.last().headers[HttpHeaders.Cookie])
            assertEquals("WebView agent", requests.last().headers[HttpHeaders.UserAgent])
        } finally {
            client.close()
        }
    }

    @Test
    fun `stale session is refreshed once after a second challenge`() = runBlocking {
        var requestCount = 0
        val refreshModes = mutableListOf<Boolean>()
        val client = HttpClient(MockEngine { request ->
            requestCount += 1
            when (request.headers[HttpHeaders.Cookie]) {
                "cf_clearance=fresh" -> respond("catalog")
                else -> respond("challenge", HttpStatusCode.Forbidden)
            }
        })
        val provider = ChallengeSessionProvider { request ->
            refreshModes += request.forceRefresh
            ChallengeSession(
                mapOf("cf_clearance" to if (request.forceRefresh) "fresh" else "stale"),
                "WebView agent",
            )
        }

        try {
            val response = ChallengeRequestExecutor(client, provider).execute(
                url = "https://source.test/catalog",
                requiredCookieNames = setOf("cf_clearance"),
            )

            assertEquals("catalog", response.bodyAsText())
            assertEquals(3, requestCount)
            assertEquals(listOf(false, true), refreshModes)
            assertTrue(refreshModes.last())
        } finally {
            client.close()
        }
    }
}
