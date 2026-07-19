package org.akkirrai.beakokit.source.yummy.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.matching.TitleMatcher
import org.akkirrai.beakokit.model.AnimeTitle
import kotlin.test.Test
import kotlin.test.assertEquals

class YummyPlaybackClientTest {
    @Test
    fun `uses yummy id and groups players into episodes`() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/anime" -> respond(
                    content = SEARCH_JSON,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )

                "/anime/357/videos" -> respond(
                    content = VIDEOS_JSON,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )

                else -> error("Unexpected URL: ${request.url}")
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }
        val provider = YummyPlaybackClient(
            client = client,
            matcher = TitleMatcher(),
            baseUrl = "https://yummy.test",
        )
        val title = AnimeTitle(
            id = "357",
            russianName = "Этот глупый свин не понимает мечту девочки-зайки",
            englishName = null,
            originalName = "Seishun Buta Yarou wa Bunny Girl Senpai no Yume wo Minai",
            japaneseName = null,
            synonyms = emptyList(),
            year = 2018,
            type = "tv",
            episodeCount = 13,
            posterUrl = null,
            status = null,
            description = null,
        )

        val match = provider.search(title).single()
        val episodes = provider.getEpisodes(match)
        val links = provider.getPlayerLinks(match, episodes.first())

        assertEquals(1.0, match.confidence)
        assertEquals(2, episodes.size)
        assertEquals(listOf("Kodik", "Aksor"), links.map { it.playerName })
        client.close()
    }

    private companion object {
        val SEARCH_JSON = """
            {
              "response": [{
                "anime_id": 357,
                "title": "Этот глупый свин не понимает мечту девочки-зайки",
                "year": 2018,
                "type": {"alias": "tv"}
              }]
            }
        """.trimIndent()

        val VIDEOS_JSON = """
            {
              "response": [
                {
                  "video_id": 1,
                  "iframe_url": "https://player.aksor.tv/video/one",
                  "data": {"dubbing": "Озвучка AniLibria", "player": "Плеер Aksor", "player_id": 1},
                  "number": "1"
                },
                {
                  "video_id": 2,
                  "iframe_url": "//kodikplayer.com/season/example?episode=1",
                  "data": {"dubbing": "Озвучка AniLibria", "player": "Плеер Kodik", "player_id": 3},
                  "number": "1"
                },
                {
                  "video_id": 3,
                  "iframe_url": "https://player.aksor.tv/video/two",
                  "data": {"dubbing": "Озвучка AniLibria", "player": "Плеер Aksor", "player_id": 1},
                  "number": "2"
                }
              ]
            }
        """.trimIndent()
    }
}
