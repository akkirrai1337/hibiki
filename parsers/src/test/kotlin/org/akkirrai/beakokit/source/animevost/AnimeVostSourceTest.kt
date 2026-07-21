package org.akkirrai.beakokit.source.animevost

import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.testkit.FixtureRoute
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.akkirrai.beakokit.testkit.SourceTestKit
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AnimeVostSourceTest {
    @Test
    fun `source satisfies catalog health and direct playback contracts`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute.fromResource("/", "beakokit/animevost/latest.html"),
                FixtureRoute.fromResource("/xfsearch/naruto/", "beakokit/animevost/search.html"),
                FixtureRoute.fromResource("/tip/tv/123-naruto.html", "beakokit/animevost/details.html"),
                FixtureRoute.fromResource("/v1/playlist", "beakokit/animevost/playlist.json", method = io.ktor.http.HttpMethod.Post),
            ),
            preferredLanguages = listOf(SourceLanguage.RUSSIAN),
            values = mapOf(
                AnimeVostConfig.BASE_URL to "https://animevost.test",
                AnimeVostConfig.API_BASE_URL to "https://api.animevost.test",
            ),
        ).use { host ->
            val source = AnimeVostSource(host.context)

            SourceTestKit.assertSourceContract(source, SourceId("animevost"))
            SourceTestKit.assertHealthCheckContract(source)
            val catalog = SourceTestKit.assertCatalogContract(source, AnimeSearchRequest(query = "naruto", limit = 5))
            val latest = SourceTestKit.assertLatestContract(source, limit = 5)
            SourceTestKit.assertFilterCatalogContract(source)
            val playback = SourceTestKit.assertPlaybackContract(source, catalog.details)

            assertEquals("tip/tv/123-naruto.html", catalog.details.id)
            assertEquals(listOf("tip/tv/123-naruto.html"), latest.map { it.id })
            assertEquals(2, playback.groups.single().episodes.size)
            assertEquals(PlayerType.DIRECT_MP4, playback.firstEpisodeLinks.first().type)
            assertEquals("https://video.animevost.test/720/episode-1.mp4", playback.firstEpisodeLinks.first().url)
        }
    }

    @Test
    fun `latest parses AnimeVost current post cards`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute.fromResource("/", "beakokit/animevost/latest-current.html"),
                FixtureRoute.fromResource(
                    "/tip/tv/3982-sora-wa-akai-kawa-no-hotori.html",
                    "beakokit/animevost/details-current.html",
                ),
            ),
            preferredLanguages = listOf(SourceLanguage.RUSSIAN),
            values = mapOf(AnimeVostConfig.BASE_URL to "https://animevost.test"),
        ).use { host ->
            val title = AnimeVostSource(host.context).latest(limit = 1).single()

            assertEquals("tip/tv/3982-sora-wa-akai-kawa-no-hotori.html", title.id)
            assertEquals(2026, title.year)
            assertEquals("tv", title.type)
            assertEquals("https://animevost.test/uploads/posts/2026-07/cover.jpg", title.posterUrl)
            assertEquals(listOf("Драма"), title.genres)
            assertNotNull(title.russianName)

            val details = AnimeVostSource(host.context).getById(title.id)
            assertEquals(12, details.episodeCount)
            assertEquals(3, details.availableEpisodeCount)
            assertEquals("ongoing", details.status)
            assertEquals("https://animevost.test/uploads/posts/2026-07/detail.jpg", details.posterUrl)
            assertEquals("Актуальное описание тайтла.", details.description)
            val today = LocalDate.now(ZoneOffset.UTC)
            val expectedDate = LocalDate.of(today.year, 7, 28).let { date ->
                if (date.isBefore(today)) date.plusYears(1) else date
            }
            assertEquals(expectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().epochSecond, details.nextEpisodeAt)
        }
    }

    @Test
    fun `blank catalog request uses AnimeVost listing pages for its offset`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute.fromResource("/page/2/", "beakokit/animevost/latest-page-2.html"),
            ),
            preferredLanguages = listOf(SourceLanguage.RUSSIAN),
            values = mapOf(AnimeVostConfig.BASE_URL to "https://animevost.test"),
        ).use { host ->
            val results = AnimeVostSource(host.context).search(AnimeSearchRequest(offset = 10, limit = 1))

            assertEquals(listOf("tip/tv/3908-gaikotsu-kishi-sama-2.html"), results.map { it.id })
        }
    }
}
