package org.akkirrai.beakokit.extension

import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.testkit.FixtureRoute
import org.akkirrai.beakokit.testkit.ScriptedExtensionFixtures
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.akkirrai.beakokit.testkit.SourceTestKit
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the Rhino-scripted KickAssAnime extension against fixtures shaped like kaa.lt's live JSON
 * API (checked 2026-08). Playback links are raw EMBED pointers to each server's own page, not
 * resolved media URLs - the extension deliberately doesn't fetch/parse them (see kickassanime.js
 * for why: the video CDN behind them 403s any non-browser HTTP client, so the app routes
 * krussdomi.com EMBED links straight into a WebView instead of the normal extractor pipeline).
 *
 * The extension payload (`kickassanime.js`) is a gitignored local-only fixture - these tests skip
 * themselves when it's absent instead of failing.
 */
class ScriptedKickAssAnimeSourceTest {
    @BeforeTest
    fun requireLocalFixture() {
        assumeTrue(ScriptedExtensionFixtures.isAvailable("kickassanime"), "kickassanime.js fixture is not present locally")
    }

    @Test
    fun `English JSON catalog and per-locale playback satisfy shared contracts`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                jsonRoute("/api/fsearch", "results.json", method = HttpMethod.Post),
                jsonRoute("/api/anime", "results.json", query = mapOf("page" to "1")),
                jsonRoute("/api/show/recent", "results.json", query = mapOf("type" to "all", "page" to "1")),
                jsonRoute("/api/show/test-show", "details.json"),
                jsonRoute("/api/show/test-show/language", "language.json"),
                jsonRoute("/api/show/test-show/episodes", "episodes.json", query = mapOf("page" to "1", "lang" to "en-US")),
                jsonRoute("/api/show/test-show/episode/ep-1-ep1abc", "servers.json"),
            ),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        ).use { host ->
            val source = ScriptedAnimeSource(host.context, ScriptedExtensionFixtures.load("kickassanime"))

            SourceTestKit.assertSourceContract(source, SourceId("kickassanime"))
            val catalog = SourceTestKit.assertCatalogContract(source, AnimeSearchRequest(query = "Test", limit = 5))
            SourceTestKit.assertTitleMetadataContract(catalog.details, requireDescription = true, requirePoster = true)
            val latest = SourceTestKit.assertLatestContract(source, limit = 2)
            val playback = SourceTestKit.assertPlaybackContract(source, catalog.details)
            val filters = SourceTestKit.assertFilterCatalogContract(source)
            val pagination = SourceTestKit.assertPaginationContract(source, AnimeSearchRequest(limit = 1))

            assertEquals("test-show", catalog.details.id)
            assertEquals("Test Show EN", catalog.details.englishName)
            assertEquals("A fixture synopsis for KickAssAnime.", catalog.details.description)
            assertEquals(AnimeReleaseStatus.ONGOING, catalog.details.releaseStatus)
            assertEquals("https://kaa.lt/image/poster/test-show-hq.jpg", catalog.details.posterUrl)
            assertEquals(listOf("test-show", "second-show"), latest.map { it.id })
            assertEquals("en-US", playback.groups.single().id)
            assertEquals(1, playback.groups.single().episodes.size)
            assertEquals(2, playback.firstEpisodeLinks.size)
            assertEquals(PlayerType.EMBED, playback.firstEpisodeLinks[0].type)
            assertEquals("https://player.test/embed/abc123", playback.firstEpisodeLinks[0].url)
            assertEquals("VidStreaming", playback.firstEpisodeLinks[0].playerName)
            assertEquals("https://player.test/embed/dash456", playback.firstEpisodeLinks[1].url)
            assertEquals("BirdStream", playback.firstEpisodeLinks[1].playerName)
            assertEquals(listOf("relevance", "rating"), filters.sortOptions.map { it.id })
            assertEquals(listOf("test-show"), pagination.firstPage.map { it.id })
            assertEquals(listOf("second-show"), pagination.secondPage.map { it.id })
            assertEquals(setOf(SourceCapability.LATEST_RELEASES, SourceCapability.PLAYBACK), source.info.capabilities)
        }
    }

    private fun jsonRoute(
        path: String,
        resource: String,
        method: HttpMethod = HttpMethod.Get,
        query: Map<String, String> = emptyMap(),
    ) = FixtureRoute.fromResource(
        path = path,
        resource = "beakokit/kickassanime/$resource",
        method = method,
        query = query,
    )
}
