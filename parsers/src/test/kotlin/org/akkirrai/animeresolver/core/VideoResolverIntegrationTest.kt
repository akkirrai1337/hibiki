package org.akkirrai.animeresolver.core

import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.Episode
import org.akkirrai.animeresolver.model.PlayerLink
import org.akkirrai.animeresolver.model.PlayerType
import org.akkirrai.animeresolver.model.ProviderMatch
import org.akkirrai.animeresolver.model.StreamType
import org.akkirrai.animeresolver.model.StreamValidationResult
import org.akkirrai.animeresolver.model.VideoStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoResolverIntegrationTest {
    @Test
    fun `resolver connects provider extractor and validator contract`() = kotlinx.coroutines.runBlocking {
        val title = AnimeTitle(
            id = "42",
            russianName = "Тест",
            englishName = "Test",
            originalName = "Test",
            japaneseName = null,
            synonyms = listOf("Test anime"),
            year = 2024,
            type = "TV",
            episodeCount = 1,
            posterUrl = null,
            status = null,
            description = null,
        )

        val provider = object : VideoProvider {
            override val id: String = "fake"
            override val name: String = "FakeProvider"

            override suspend fun search(title: AnimeTitle): List<ProviderMatch> =
                listOf(
                    ProviderMatch(
                        providerId = id,
                        providerName = name,
                        mediaId = "media-1",
                        title = "Fake title",
                        confidence = 0.99,
                        year = title.year,
                        type = title.type,
                        episodeCount = 1,
                    ),
                )

            override suspend fun getEpisodes(match: ProviderMatch): List<Episode> =
                listOf(Episode(id = "ep-1", number = 1.0, title = "Pilot"))

            override suspend fun getPlayerLinks(match: ProviderMatch, episode: Episode): List<PlayerLink> =
                listOf(
                    PlayerLink(
                        url = "https://player.example/stream.m3u8",
                        type = PlayerType.DIRECT_HLS,
                        quality = "1080p",
                    ),
                )
        }

        var extractedLink: PlayerLink? = null
        val extractor = object : PlayerExtractor {
            override fun supports(link: PlayerLink): Boolean = link.type == PlayerType.DIRECT_HLS

            override suspend fun extract(link: PlayerLink): VideoStream {
                extractedLink = link
                return VideoStream(
                    url = link.url,
                    type = StreamType.HLS,
                    quality = link.quality,
                    headers = link.headers,
                )
            }
        }

        val validator = object : StreamValidator {
            override suspend fun validate(stream: VideoStream): StreamValidationResult =
                StreamValidationResult(
                    success = true,
                    streamType = stream.type,
                    quality = stream.quality,
                    finalUrl = stream.url,
                    statusCode = 200,
                    message = "ok",
                )
        }

        val resolver = VideoResolver(
            providers = listOf(provider),
            extractors = listOf(extractor),
            validator = validator,
        )

        val discovery = resolver.discoverSources(title)
        assertEquals(1, discovery.matches.size)
        assertTrue(discovery.failures.isEmpty())

        val episodes = resolver.getEpisodes(discovery.matches.single())
        assertEquals(1, episodes.size)

        val result = resolver.resolveAndValidate(discovery.matches.single(), episodes.single())
        assertTrue(result.success)
        assertEquals("ok", result.message)
        assertEquals("https://player.example/stream.m3u8", extractedLink?.url)
    }
}
