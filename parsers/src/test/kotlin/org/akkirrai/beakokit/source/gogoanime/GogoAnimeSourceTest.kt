package org.akkirrai.beakokit.source.gogoanime

import io.ktor.http.ContentType
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.testkit.FixtureRoute
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.akkirrai.beakokit.testkit.SourceTestKit
import kotlin.test.Test
import kotlin.test.assertEquals

class GogoAnimeSourceTest {
    @Test
    fun `English HTML catalog and embedded playback satisfy shared contracts`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                htmlRoute("/search", "search.html", mapOf("keyword" to "Test", "page" to "1")),
                htmlRoute("/search", "search-page-2.html", mapOf("keyword" to "Test", "page" to "2")),
                htmlRoute("/", "latest.html"),
                htmlRoute("/category/test-show", "details.html"),
                htmlRoute("/test-show-episode-1", "episode.html"),
                htmlRoute("/test-show-episode-2", "episode.html"),
            ),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
            values = mapOf(GogoAnimeConfig.BASE_URL to "https://gogoanime.test"),
        ).use { host ->
            val source = GogoAnimeSource(host.context)

            SourceTestKit.assertSourceContract(source, SourceId("gogoanime"))
            val catalog = SourceTestKit.assertCatalogContract(
                source,
                AnimeSearchRequest(query = "Test", limit = 1),
            )
            SourceTestKit.assertTitleMetadataContract(
                catalog.details,
                requireDescription = true,
                requirePoster = true,
            )
            val latest = SourceTestKit.assertLatestContract(source, limit = 1)
            val playback = SourceTestKit.assertPlaybackContract(source, catalog.details)
            val filters = SourceTestKit.assertFilterCatalogContract(source)
            val pagination = SourceTestKit.assertPaginationContract(
                source,
                AnimeSearchRequest(query = "Test", limit = 1),
            )

            assertEquals("test-show", catalog.details.id)
            assertEquals("Test Show", catalog.details.originalName)
            assertEquals("A fixture synopsis for GogoAnime.", catalog.details.description)
            assertEquals("テストショー", catalog.details.synonyms.last())
            assertEquals(2, catalog.details.episodeCount)
            assertEquals(2, catalog.details.availableEpisodeCount)
            assertEquals(AnimeReleaseStatus.RELEASED, catalog.details.releaseStatus)
            assertEquals("https://gogoanime.test/poster/test.jpg", catalog.details.posterUrl)
            assertEquals(listOf("test-show"), latest.map { it.id })
            assertEquals(setOf("dub", "sub"), playback.groups.map { it.id.substringAfterLast(':') }.toSet())
            assertEquals(PlayerType.EMBED, playback.firstEpisodeLinks.single().type)
            assertEquals("English dub", playback.firstEpisodeLinks.single().translation)
            assertEquals(listOf("relevance"), filters.sortOptions.map { it.id })
            assertEquals(listOf("test-show"), pagination.firstPage.map { it.id })
            assertEquals(listOf("second-show"), pagination.secondPage.map { it.id })
            assertEquals(
                setOf(SourceCapability.LATEST_RELEASES, SourceCapability.PLAYBACK),
                source.info.capabilities,
            )
        }
    }

    private fun htmlRoute(
        path: String,
        resource: String,
        query: Map<String, String> = emptyMap(),
    ) = FixtureRoute.fromResource(
        path = path,
        resource = "beakokit/gogoanime/$resource",
        query = query,
        contentType = ContentType.Text.Html,
    )
}
