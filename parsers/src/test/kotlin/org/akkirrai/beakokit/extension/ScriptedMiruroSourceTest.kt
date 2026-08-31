package org.akkirrai.beakokit.extension

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.context.DefaultSourceContext
import org.akkirrai.beakokit.testkit.ScriptedExtensionFixtures
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the Rhino-scripted Miruro extension against a real captured `/api/secure/pipe` response
 * for `info/178789` (Mushoku Tensei III) - the body is genuinely "x-obfuscated: 2" (base64url ->
 * XOR with Miruro's own plaintext-shipped key -> gzip), so this exercises the actual decode path,
 * not a synthetic stand-in for it.
 *
 * The extension payload (`miruro.js`) is a gitignored local-only fixture - this test skips itself
 * when it's absent instead of failing.
 */
class ScriptedMiruroSourceTest {
    @Test
    fun `getById decodes a real captured obfuscated pipe response`() = runBlocking {
        assumeTrue(ScriptedExtensionFixtures.isAvailable("miruro"), "miruro.js fixture is not present locally")

        val bodyFile = File("src/test/resources/beakokit/miruro/info-178789-obfuscated.txt")
        assumeTrue(bodyFile.exists(), "captured obfuscated fixture body is not present locally")
        val obfuscatedBody = bodyFile.readText().trim()

        val mockEngine = MockEngine { request ->
            assertTrue(request.url.encodedPath == "/api/secure/pipe", "unexpected path: ${request.url.encodedPath}")
            respond(
                content = obfuscatedBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("text/plain"), "x-obfuscated" to listOf("2")),
            )
        }
        val context = DefaultSourceContext(
            httpClient = HttpClient(mockEngine),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        )
        val source = ScriptedAnimeSource(context, ScriptedExtensionFixtures.load("miruro"))

        val title = source.getById("178789")

        assertEquals("178789", title.id)
        assertEquals("Mushoku Tensei: Jobless Reincarnation Season 3", title.englishName)
        assertEquals("TV", title.type?.uppercase())
        assertEquals("ongoing", title.status)
        assertTrue(title.genres.contains("Fantasy"))
        assertTrue(title.studios.contains("Studio Bind"))
        assertTrue(title.posterUrl?.contains("anilistcdn") == true)
    }
}
