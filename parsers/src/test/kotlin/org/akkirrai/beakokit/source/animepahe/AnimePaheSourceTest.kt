package org.akkirrai.beakokit.source.animepahe

import io.ktor.http.ContentType
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.testkit.FixtureRoute
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.akkirrai.beakokit.testkit.SourceTestKit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnimePaheSourceTest {
    @Test
    fun `English API catalog and Kwik playback satisfy shared contracts`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                htmlRoute("/search", "search.html", mapOf("q" to "Test")),
                htmlRoute("/latest-updated", "latest.html"),
                jsonRoute("/viewApi", "releases-1.json", mapOf("m" to "release", "id" to "test-session", "page" to "1")),
                htmlRoute("/anime/test-session", "details.html"),
                htmlRoute("/anime/another-session", "another-details.html"),
                htmlRoute("/play/test-session/episode-one", "play.html"),
                jsonRoute("/anime/get-servers/episode-one", "servers.json"),
                jsonRoute("/anime/get-servers/episode-two", "servers-sub-only.json"),
            ),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
            values = mapOf(AnimePaheConfig.BASE_URL to "https://animepahe.test"),
        ).use { host ->
            val source = AnimePaheSource(host.context)

            SourceTestKit.assertSourceContract(source, SourceId("animepahe"))
            val catalog = SourceTestKit.assertCatalogContract(
                source,
                AnimeSearchRequest(query = "Test", limit = 5),
            )
            SourceTestKit.assertTitleMetadataContract(
                catalog.details,
                requireDescription = true,
                requirePoster = true,
            )
            val latest = SourceTestKit.assertLatestContract(source, limit = 2)
            val playback = SourceTestKit.assertPlaybackContract(source, catalog.details)
            val filters = SourceTestKit.assertFilterCatalogContract(source)
            val pagination = SourceTestKit.assertPaginationContract(
                source,
                AnimeSearchRequest(limit = 1),
            )

            assertEquals("test-session", catalog.details.id)
            assertEquals("Test Show", catalog.details.originalName)
            assertEquals("A fixture synopsis for AnimePahe.", catalog.details.description)
            assertEquals(2, catalog.details.episodeCount)
            assertEquals(AnimeReleaseStatus.RELEASED, catalog.details.releaseStatus)
            assertEquals("https://cdn.test/test.jpg", catalog.details.posterUrl)
            assertEquals(listOf("test-session", "another-session"), latest.map { it.id })
            assertEquals(2, playback.groups.single().episodes.size)
            assertEquals(PlayerType.EMBED, playback.firstEpisodeLinks.single().type)
            assertEquals(null, playback.firstEpisodeLinks.single().quality)
            assertEquals("English dub", playback.firstEpisodeLinks.single().translation)
            val group = source.getPlaybackGroups(catalog.details).single()
            val secondEpisodeLinks = source.getPlayerLinks(catalog.details, group, group.episodes[1])
            assertEquals(
                listOf(
                    "https://megaplay.buzz/stream/s-2/1465/dub",
                    "https://vidwish.live/stream/s-2/1465/dub",
                ),
                secondEpisodeLinks.map { it.url },
            )
            assertEquals(listOf("relevance"), filters.sortOptions.map { it.id })
            assertEquals(listOf("test-session"), pagination.firstPage.map { it.id })
            assertEquals(listOf("another-session"), pagination.secondPage.map { it.id })
            assertEquals(
                setOf(SourceCapability.LATEST_RELEASES, SourceCapability.PLAYBACK),
                source.info.capabilities,
            )
            assertTrue(host.requests.any { it.url.encodedPath == "/search" })
            assertTrue(host.requests.any { it.url.parameters["sort"] == "episode_asc" })
            assertTrue(host.requests.all { it.headers["User-Agent"]?.startsWith("Mozilla/5.0") == true })
            assertTrue(
                host.requests
                    .filter { it.url.encodedPath == "/viewApi" || it.url.encodedPath.startsWith("/anime/get-servers/") }
                    .all { it.headers["X-Requested-With"] == "XMLHttpRequest" },
            )
        }
    }

    private fun jsonRoute(path: String, resource: String, query: Map<String, String> = emptyMap()) = FixtureRoute.fromResource(
        path = path,
        resource = "beakokit/animepahe/$resource",
        query = query,
    )

    private fun htmlRoute(path: String, resource: String, query: Map<String, String> = emptyMap()) = FixtureRoute.fromResource(
        path = path,
        resource = "beakokit/animepahe/$resource",
        query = query,
        contentType = ContentType.Text.Html,
    )
}
