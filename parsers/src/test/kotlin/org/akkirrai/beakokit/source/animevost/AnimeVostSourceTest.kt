package org.akkirrai.beakokit.source.animevost

import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.testkit.FixtureRoute
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.akkirrai.beakokit.testkit.SourceTestKit
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
