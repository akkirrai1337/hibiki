package org.akkirrai.beakokit.source.yummy

import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.testkit.JsonFixtureRoute
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.akkirrai.beakokit.testkit.SourceTestKit
import kotlin.test.Test
import kotlin.test.assertEquals

class YummyAnimeSourceTest {
    @Test
    fun `source owns identity and consumes host language and secret`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(JsonFixtureRoute("/anime", """{"response":[]}""")),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
            values = mapOf(YummyAnimeConfig.BASE_URL to "https://yummy.test"),
            secrets = mapOf(YummyAnimeConfig.APPLICATION_TOKEN to "application-secret"),
        ).use { host ->
            val source = YummyAnimeSource(host.context)

            SourceTestKit.assertSourceContract(source, SourceId("yummy-anime"))
            assertEquals(SourceId("yummy-anime"), source.info.id)
            assertEquals("YummyAnime", source.name)
            assertEquals(SourceCapability.entries.toSet(), source.info.capabilities)
            assertEquals(emptyList(), source.search("frieren"))
            assertEquals("en", host.requests.single().headers["Lang"])
            assertEquals("application-secret", host.requests.single().headers["X-Application"])
        }
    }

    @Test
    fun `playback stays behind source contract`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                JsonFixtureRoute(
                    path = "/anime/987654/videos",
                    body = """
                        {"response":[{
                          "video_id":1,
                          "iframe_url":"https://player.test/episode-1",
                          "data":{"dubbing":"Voice","player":"Плеер Kodik","player_id":3},
                          "number":"1"
                        }]}
                    """.trimIndent(),
                ),
            ),
            preferredLanguages = listOf(SourceLanguage.RUSSIAN),
            values = mapOf(YummyAnimeConfig.BASE_URL to "https://yummy.test"),
            secrets = mapOf(YummyAnimeConfig.APPLICATION_TOKEN to "application-secret"),
        ).use { host ->
            val source = YummyAnimeSource(host.context)
            val title = title("987654")

            val snapshot = SourceTestKit.assertPlaybackContract(source, title)
            val group = snapshot.groups.single()
            val links = snapshot.firstEpisodeLinks

            assertEquals("Voice", group.title)
            assertEquals(listOf("Kodik"), links.map { it.playerName })
            assertEquals("application-secret", host.requests.single().headers["X-Application"])
        }
    }

    private fun title(id: String) = AnimeTitle(
        id = id,
        russianName = "Test",
        englishName = null,
        originalName = "Test",
        japaneseName = null,
        synonyms = emptyList(),
        year = 2026,
        type = "tv",
        episodeCount = 1,
        posterUrl = null,
        status = null,
        description = null,
    )
}
