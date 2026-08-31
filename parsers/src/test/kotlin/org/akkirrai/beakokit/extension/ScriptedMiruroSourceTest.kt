package org.akkirrai.beakokit.extension

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.BrowserFetchProvider
import org.akkirrai.beakokit.api.BrowserFetchRequest
import org.akkirrai.beakokit.api.BrowserFetchResponse
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.context.DefaultSourceContext
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.testkit.ScriptedExtensionFixtures
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the Rhino-scripted Miruro extension against real captured `/api/secure/pipe` responses
 * (obfuscated: base64url -> XOR with Miruro's own plaintext-shipped key -> gzip), exercising the
 * actual decode path rather than a synthetic stand-in for it. miruro.js resolves every pipe call
 * through the host's `browserFetch()` global (not plain `fetch()`) - a real device's own IP still
 * gets a genuine Cloudflare 403 on a plain fetch, since the WAF binds its pass to the exact client
 * fingerprint (TLS/JA3), not just a cookie - so these tests stub [BrowserFetchProvider] instead of
 * mocking an HTTP client.
 *
 * The extension payload (`miruro.js`) is a gitignored local-only fixture - this test skips itself
 * when it's absent instead of failing.
 */
class ScriptedMiruroSourceTest {
    private fun stubBrowserFetch(pathToBody: Map<String, File>): BrowserFetchProvider = BrowserFetchProvider { request ->
        val e = java.net.URI(request.targetUrl).query.orEmpty().removePrefix("e=")
        val standard = e.replace('-', '+').replace('_', '/')
        val padded = standard + "=".repeat((4 - standard.length % 4) % 4)
        val envelope = String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
        val body = pathToBody.entries.firstOrNull { (path, _) -> envelope.contains("\"path\":\"$path") }?.value
            ?: error("Unexpected pipe path in envelope: $envelope")
        BrowserFetchResponse(status = 200, body = body.readText().trim(), headers = mapOf("x-obfuscated" to "2"))
    }

    private fun noNetworkContext(browserFetchProvider: BrowserFetchProvider) = DefaultSourceContext(
        httpClient = HttpClient(MockEngine { error("This test performs no direct HTTP I/O; miruro.js must use browserFetch()") }),
        preferredLanguages = listOf(SourceLanguage.ENGLISH),
        browserFetchProvider = browserFetchProvider,
    )

    @Test
    fun `getById decodes a real captured obfuscated pipe response`() = runBlocking {
        assumeTrue(ScriptedExtensionFixtures.isAvailable("miruro"), "miruro.js fixture is not present locally")

        val bodyFile = File("src/test/resources/beakokit/miruro/info-178789-obfuscated.txt")
        assumeTrue(bodyFile.exists(), "captured obfuscated fixture body is not present locally")

        val context = noNetworkContext(stubBrowserFetch(mapOf("info" to bodyFile)))
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

    @Test
    fun `getPlaybackGroups and getPlayerLinks decode real captured obfuscated responses`() = runBlocking {
        assumeTrue(ScriptedExtensionFixtures.isAvailable("miruro"), "miruro.js fixture is not present locally")

        val infoBody = File("src/test/resources/beakokit/miruro/info-178789-obfuscated.txt")
        val episodesBody = File("src/test/resources/beakokit/miruro/episodes-178789-obfuscated.txt")
        val sourcesBody = File("src/test/resources/beakokit/miruro/sources-bee-sub-obfuscated.txt")
        assumeTrue(episodesBody.exists() && sourcesBody.exists(), "captured obfuscated fixture bodies are not present locally")

        val context = noNetworkContext(
            stubBrowserFetch(mapOf("info" to infoBody, "episodes" to episodesBody, "sources" to sourcesBody)),
        )
        val source = ScriptedAnimeSource(context, ScriptedExtensionFixtures.load("miruro"))
        val title = source.getById("178789")

        val groups = source.getPlaybackGroups(title)
        assertTrue(groups.isNotEmpty(), "expected at least one playback group")
        val beeSub = groups.singleOrNull { it.id == "bee:sub" }
        assertTrue(beeSub != null, "expected a bee:sub group, got: ${groups.map { it.id }}")
        assertTrue(beeSub!!.episodes.isNotEmpty())

        val links = source.getPlayerLinks(title, beeSub, beeSub.episodes.first())
        assertTrue(links.isNotEmpty(), "expected at least one player link from bee:sub")
        assertTrue(links.any { it.type == PlayerType.DIRECT_HLS }, "expected at least one DIRECT_HLS link")
        val hls = links.first { it.type == PlayerType.DIRECT_HLS }
        assertTrue(hls.url.endsWith(".m3u8") || hls.url.contains(".m3u8"))
        assertEquals("Sub", hls.translation)
    }

    /**
     * Regression test for a real bug caught live: the server hard-caps `search/browse`'s `perPage`
     * at 50 (a larger value 502s), but the old code requested `perPage = min(offset + limit, 50)`
     * on page 1 and sliced `[offset, offset + limit)` out of it - for any offset >= 50 that slice
     * fell entirely outside the single page fetched, silently returning an empty catalog (surfaced
     * as a permanently-empty Home screen once its randomized trending offset exceeded 50). The fix
     * fetches however many consecutive 50-item pages actually cover the requested window.
     */
    @Test
    fun `search paginates past the server's 50-item page cap`() = runBlocking {
        assumeTrue(ScriptedExtensionFixtures.isAvailable("miruro"), "miruro.js fixture is not present locally")

        val browserFetchProvider = BrowserFetchProvider { request ->
            val e = java.net.URI(request.targetUrl).query.orEmpty().removePrefix("e=")
            val standard = e.replace('-', '+').replace('_', '/')
            val padded = standard + "=".repeat((4 - standard.length % 4) % 4)
            val envelope = String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
            val pageMatch = Regex("\"page\":(\\d+)").find(envelope) ?: error("no page in envelope: $envelope")
            val perPageMatch = Regex("\"perPage\":(\\d+)").find(envelope) ?: error("no perPage in envelope: $envelope")
            val page = pageMatch.groupValues[1].toInt()
            val perPage = perPageMatch.groupValues[1].toInt()
            require(perPage <= 50) { "server would 502 above perPage=50, got $perPage" }
            val start = (page - 1) * perPage
            val items = (start until start + perPage).joinToString(",") { id ->
                """{"id":$id,"idMal":$id,"title":{"english":"Title $id"},"coverImage":{},"format":"TV","status":"RELEASING"}"""
            }
            BrowserFetchResponse(status = 200, body = "[$items]")
        }
        val context = noNetworkContext(browserFetchProvider)
        val source = ScriptedAnimeSource(context, ScriptedExtensionFixtures.load("miruro"))

        val results = source.search(AnimeSearchRequest(limit = 24, offset = 99, sort = AnimeSearchSort.RATING))

        assertEquals(24, results.size)
        assertEquals((99..122).map { it.toString() }, results.map { it.id })
    }
}
