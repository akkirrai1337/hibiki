package org.akkirrai.beakokit.extension

import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.testkit.FixtureRoute
import org.akkirrai.beakokit.testkit.ScriptedExtensionFixtures
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.akkirrai.beakokit.testkit.SourceTestKit
import org.akkirrai.beakokit.testkit.TitleMetadataRequirements
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Proves the Rhino-scripted AnimeVost extension end-to-end against the same fixture pages the old
 * compiled-in AnimeVostSource used. The second test's description assertion is the regression
 * check for the catalog-description bug reported against the old Kotlin parser (it rejected every
 * `<p>` containing a `<strong>`, which also rejected the real
 * `<p><strong>Описание: </strong>...</p>` paragraph).
 *
 * The extension payload (`animevost.js`) is a gitignored local-only fixture, not part of the main
 * repo - these tests skip themselves when it's absent instead of failing.
 */
class ScriptedAnimeVostSourceTest {
    @BeforeTest
    fun requireLocalFixture() {
        assumeTrue(ScriptedExtensionFixtures.isAvailable("animevost"), "animevost.js fixture is not present locally")
    }

    @Test
    fun `scripted source satisfies catalog health and direct playback contracts`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute.fromResource("/", "beakokit/animevost/latest.html"),
                FixtureRoute.fromResource("/xfsearch/naruto/", "beakokit/animevost/search.html"),
                FixtureRoute.fromResource("/tip/tv/123-naruto.html", "beakokit/animevost/details.html"),
                FixtureRoute.fromResource("/v1/playlist", "beakokit/animevost/playlist-http.json", method = HttpMethod.Post),
            ),
            preferredLanguages = listOf(SourceLanguage.RUSSIAN),
        ).use { host ->
            val source = ScriptedAnimeSource(host.context, ScriptedExtensionFixtures.load("animevost"))

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
            assertEquals("http://video.animetop.info/720/episode-1.mp4", playback.firstEpisodeLinks.first().url)
        }
    }

    @Test
    fun `latest parses AnimeVost current post cards and includes a catalog description`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute.fromResource("/", "beakokit/animevost/latest-current.html"),
                FixtureRoute.fromResource(
                    "/tip/tv/3982-sora-wa-akai-kawa-no-hotori.html",
                    "beakokit/animevost/details-current.html",
                ),
                FixtureRoute.fromResource("/page/2/", "beakokit/animevost/latest-page-2.html"),
            ),
            preferredLanguages = listOf(SourceLanguage.RUSSIAN),
        ).use { host ->
            val source = ScriptedAnimeSource(host.context, ScriptedExtensionFixtures.load("animevost"))
            val title = SourceTestKit.assertLatestContract(source, limit = 1).single()

            assertEquals("tip/tv/3982-sora-wa-akai-kawa-no-hotori.html", title.id)
            assertEquals(2026, title.year)
            assertEquals("tv", title.type)
            assertEquals("https://animevost.org/uploads/posts/2026-07/cover.jpg", title.posterUrl)
            assertEquals(listOf("Драма"), title.genres)
            assertNotNull(title.russianName)
            assertEquals("Каталожное описание тайтла для проверки бага с парсингом.", title.description)

            val details = SourceTestKit.assertDetailsMetadataContract(
                source = source,
                id = title.id,
                requirements = TitleMetadataRequirements(
                    description = true,
                    poster = true,
                    releaseStatus = true,
                    episodeCount = true,
                    availableEpisodeCount = true,
                    nextEpisodeAt = true,
                ),
            )
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

            val pagination = SourceTestKit.assertPaginationContract(
                source = source,
                request = AnimeSearchRequest(limit = 10),
            )
            assertEquals(listOf("tip/tv/3982-sora-wa-akai-kawa-no-hotori.html"), pagination.firstPage.map { it.id })
            assertEquals(listOf("tip/tv/3908-gaikotsu-kishi-sama-2.html"), pagination.secondPage.map { it.id })
        }
    }
}
