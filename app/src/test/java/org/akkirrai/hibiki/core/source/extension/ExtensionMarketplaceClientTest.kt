package org.akkirrai.hibiki.core.source.extension

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
        val client = ExtensionMarketplaceClient(textPlainClient("[]"))

        assertEquals(
            "https://raw.githubusercontent.com/example/repo/refs/heads/main/index.min.json",
            client.stableRepositoryUrl("https://raw.githubusercontent.com/example/repo/main/index.min.json"),
        )
        assertEquals(
            "https://example.com/repository/index.json",
            client.stableRepositoryUrl("https://example.com/repository/index.json"),
        )
    }

    @Test
    fun `the default repository is the Aniyomi anime extensions one`() {
        assertEquals(
            "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
            ExtensionMarketplaceClient.DEFAULT_INDEX_URL,
        )
    }

    @Test
    fun `fetchCatalog decodes a text-plain-served APK index and resolves apk and icon urls`() = runBlocking {
        val client = textPlainClient(
            """
            [
              {
                "name": "Aniyomi: AnimeOnsen",
                "pkg": "eu.kanade.tachiyomi.animeextension.all.animeonsen",
                "apk": "aniyomi-all.animeonsen-v14.10.apk",
                "lang": "all",
                "version": "14.10",
                "nsfw": 0
              }
            ]
            """.trimIndent(),
        )
        val extensions = ExtensionMarketplaceClient(client, "https://example.com/repo/index.min.json").fetchCatalog()

        assertEquals(1, extensions.size)
        val extension = extensions.single()
        assertEquals("eu.kanade.tachiyomi.animeextension.all.animeonsen", extension.pkg)
        assertEquals("https://example.com/repo/apk/aniyomi-all.animeonsen-v14.10.apk", extension.downloadUrl)
        assertEquals(
            "https://example.com/repo/icon/eu.kanade.tachiyomi.animeextension.all.animeonsen.png",
            extension.iconUrl,
        )
        client.close()
    }

    @Test
    fun `fetchCatalog throws on a non-success status`() = runBlocking {
        val client = textPlainClient("not found", status = HttpStatusCode.NotFound)
        assertThrowsMarketplaceException { ExtensionMarketplaceClient(client).fetchCatalog() }
        client.close()
    }

    @Test
    fun `fetchCatalog throws on invalid JSON instead of crashing`() = runBlocking {
        val client = textPlainClient("<html>not json</html>")
        assertThrowsMarketplaceException { ExtensionMarketplaceClient(client).fetchCatalog() }
        client.close()
    }

    @Test
    fun `fetchCatalog rejects an index that is not an APK array`() = runBlocking {
        val client = textPlainClient("""{"schemaVersion":1,"extensions":[]}""")
        assertThrowsMarketplaceException { ExtensionMarketplaceClient(client).fetchCatalog() }
        client.close()
    }

    @Test
    fun `fetchCatalog rejects a non-HTTPS repository URL`() = runBlocking {
        val client = textPlainClient("[]")
        assertThrowsMarketplaceException {
            ExtensionMarketplaceClient(client, "http://example.com/repository/index.min.json").fetchCatalog()
        }
        client.close()
    }

    @Test
    fun `isExtensionVersionNewer compares dotted versions numerically`() {
        assertTrue(isExtensionVersionNewer("1.1.0", "1.0.0"))
        assertTrue(isExtensionVersionNewer("2.0.0", "1.9.9"))
        assertTrue(isExtensionVersionNewer("14.10", "14.9"))
        assertTrue(!isExtensionVersionNewer("1.0.0", "1.0.0"))
        assertTrue(!isExtensionVersionNewer("1.0.0", "1.1.0"))
        assertTrue(!isExtensionVersionNewer("not-a-version", "1.0.0"))
    }

    @Test
    fun `an update is offered only for an installed extension with a newer version`() {
        val extension = ApkRepositoryExtension(name = "X", pkg = "pkg.x", apk = "x.apk", version = "14.11")
        assertTrue(extension.isUpdateAvailable(mapOf("pkg.x" to "14.10")))
        assertTrue(!extension.isUpdateAvailable(mapOf("pkg.x" to "14.11")))
        assertTrue(!extension.isUpdateAvailable(emptyMap()))
    }

    private suspend fun assertThrowsMarketplaceException(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected ExtensionMarketplaceException")
        } catch (expected: ExtensionMarketplaceException) {
            // expected
        }
    }
}
