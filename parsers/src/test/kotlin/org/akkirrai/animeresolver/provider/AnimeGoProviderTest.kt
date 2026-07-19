package org.akkirrai.animeresolver.provider

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
import kotlin.test.assertTrue

class AnimeGoProviderTest {
    @Test
    fun `parses search episodes and players`() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/search/all" -> respond(
                    content = SEARCH_HTML,
                    headers = headersOf(HttpHeaders.ContentType, "text/html"),
                )

                "/player/710" -> respond(
                    content = """{"status":"success","data":{"content":${jsonString(EPISODES_HTML)}}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )

                "/player/videos/10934" -> respond(
                    content = """{"status":"success","data":{"content":${jsonString(PLAYERS_HTML)}}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )

                else -> error("Unexpected URL: ${request.url}")
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
            }
        }
        val provider = AnimeGoProvider(client, TitleMatcher(), "https://animego.test")
        val title = AnimeTitle(
            id = "37450",
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
        val episode = provider.getEpisodes(match).single()
        val players = provider.getPlayerLinks(match, episode)

        assertEquals("710", match.mediaId)
        assertEquals(1.0, episode.number)
        assertEquals("AniBoom", players.first().playerName)
        assertTrue(players.first().url.startsWith("https://aniboom.one/"))
        client.close()
    }

    private fun jsonString(value: String): String =
        kotlinx.serialization.json.Json.encodeToString(value)

    private companion object {
        val SEARCH_HTML = """
            <div class="ani-grid__item">
              <div class="ani-grid__item-body">
                <div class="fw-lighter">Seishun Buta Yarou wa Bunny Girl Senpai no Yume wo Minai</div>
                <div class="ani-grid__item-title">
                  <a title="Этот глупый свин не понимает мечту девочки-зайки"
                     href="/anime/seishun-buta-yarou-wa-bunny-girl-senpai-no-yume-wo-minai-710">Title</a>
                </div>
                <div class="ani-grid__item-genres">
                  <span class="ani-grid__item-genres__link">Сериал</span>
                  <span class="ani-grid__item-genres__link">2018</span>
                </div>
              </div>
            </div>
        """.trimIndent()

        val EPISODES_HTML = """
            <div class="player-video-bar__item"
                 data-episode="10934"
                 data-episode-number="1"
                 data-episode-title="My Senpai is a Bunny Girl"></div>
        """.trimIndent()

        val PLAYERS_HTML = """
            <button data-player="//kodikplayer.com/example"
                    data-provider-title="Kodik"
                    data-translation-title="AniLibria"></button>
            <button data-player="//aniboom.one/embed/example"
                    data-provider-title="AniBoom"
                    data-translation-title="AniLibria"></button>
        """.trimIndent()
    }
}
