package org.akkirrai.hibiki.core.source.extension

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExtensionMarketplaceClientTest {
    // raw.githubusercontent.com serves .json as text/plain, not application/json - these fixtures
    // deliberately mirror that so a regression back to ktor's auto content-negotiation is caught.
    private fun textPlainClient(body: String, status: HttpStatusCode = HttpStatusCode.OK) = HttpClient(
        MockEngine {
            respond(content = body, status = status, headers = headersOf("Content-Type", "text/plain; charset=utf-8"))
        },
    )

    @Test
    fun `GitHub Raw main URLs use the fully qualified branch reference`() {
        val client = ExtensionMarketplaceClient(textPlainClient("{}"))

        assertEquals(
            "https://raw.githubusercontent.com/akkirrai1337/hibiki-sources/refs/heads/main/repository/index.json",
            client.stableRepositoryUrl(
                "https://raw.githubusercontent.com/akkirrai1337/hibiki-sources/main/repository/index.json",
            ),
        )
        assertEquals(
            "https://example.com/repository/index.json",
            client.stableRepositoryUrl("https://example.com/repository/index.json"),
        )
    }

    @Test
    fun `fetchIndex decodes a text-plain-served index`() = runBlocking {
        val client = textPlainClient(
            """
            {
              "schemaVersion": 1,
              "extensions": [
                {
                  "id": "animevost",
                  "name": "AnimeVost",
                  "version": "1.0.0",
                  "lang": "ru",
                  "capabilities": ["LATEST_RELEASES", "PLAYBACK"],
                  "manifestUrl": "https://example.com/animevost.json"
                }
              ]
            }
            """.trimIndent(),
        )
        val index = ExtensionMarketplaceClient(client).fetchIndex()

        assertEquals(1, index.schemaVersion)
        assertEquals(listOf("animevost"), index.extensions.map { it.id })
        client.close()
    }

    @Test
    fun `fetchIndex throws on a non-success status`() = runBlocking {
        val client = textPlainClient("not found", status = HttpStatusCode.NotFound)
        assertThrowsMarketplaceException { ExtensionMarketplaceClient(client).fetchIndex() }
        client.close()
    }

    @Test
    fun `fetchIndex throws on invalid JSON instead of crashing`() = runBlocking {
        val client = textPlainClient("<html>not json</html>")
        assertThrowsMarketplaceException { ExtensionMarketplaceClient(client).fetchIndex() }
        client.close()
    }

    private suspend fun assertThrowsMarketplaceException(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected ExtensionMarketplaceException")
        } catch (expected: ExtensionMarketplaceException) {
            // expected
        }
    }

    @Test
    fun `fetchManifest fetches the manifest and its sibling js payload and merges them`() = runBlocking {
        val client = HttpClient(
            MockEngine { request ->
                val body = when (request.url.toString().substringBefore('?')) {
                    "https://example.com/animevost.manifest.json" ->
                        """{"id":"animevost","name":"AnimeVost","version":"1.0.0","lang":"ru","capabilities":["LATEST_RELEASES","PLAYBACK"]}"""
                    "https://example.com/animevost.js" -> "var Provider = {};"
                    else -> error("Unexpected request: ${request.url}")
                }
                respond(content = body, status = HttpStatusCode.OK, headers = headersOf("Content-Type", "text/plain; charset=utf-8"))
            },
        )
        val extension = MarketplaceExtension(
            id = "animevost",
            name = "AnimeVost",
            version = "1.0.0",
            lang = "ru",
            manifestUrl = "https://example.com/animevost.manifest.json",
        )

        val merged = ExtensionMarketplaceClient(client).fetchManifest(extension)

        assertTrue(merged.contains("\"id\":\"animevost\""))
        assertTrue(merged.contains("var Provider = {};"))
        client.close()
    }

    @Test
    fun `fetchManifest skips the payload fetch when the manifest already inlines it`() = runBlocking {
        val client = HttpClient(
            MockEngine { request ->
                assertEquals(
                    "https://example.com/animevost.manifest.json",
                    request.url.toString().substringBefore('?'),
                )
                respond(
                    content = """{"id":"animevost","name":"AnimeVost","version":"1.0.0","lang":"ru","capabilities":["LATEST_RELEASES","PLAYBACK"],"payload":"var Provider = {};"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "text/plain; charset=utf-8"),
                )
            },
        )
        val extension = MarketplaceExtension(
            id = "animevost",
            name = "AnimeVost",
            version = "1.0.0",
            lang = "ru",
            manifestUrl = "https://example.com/animevost.manifest.json",
        )

        val merged = ExtensionMarketplaceClient(client).fetchManifest(extension)

        assertTrue(merged.contains("var Provider = {};"))
        client.close()
    }

    @Test
    fun `isExtensionVersionNewer compares basic semver`() {
        assertTrue(isExtensionVersionNewer("1.1.0", "1.0.0"))
        assertTrue(isExtensionVersionNewer("2.0.0", "1.9.9"))
        assertTrue(!isExtensionVersionNewer("1.0.0", "1.0.0"))
        assertTrue(!isExtensionVersionNewer("1.0.0", "1.1.0"))
        assertTrue(!isExtensionVersionNewer("not-a-version", "1.0.0"))
    }
}
