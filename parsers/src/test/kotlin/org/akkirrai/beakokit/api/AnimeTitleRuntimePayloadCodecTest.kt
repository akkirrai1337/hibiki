package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.AnimeTrailerTitle
import org.akkirrai.beakokit.model.CharacterTitle
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.RelatedAnimeTitle
import org.akkirrai.beakokit.model.TitleRating
import org.akkirrai.beakokit.model.VideoSegment
import org.akkirrai.beakokit.model.VideoSegmentType

class AnimeTitleRuntimePayloadCodecTest {
    @Test
    fun fullTitleRoundTripsWithoutDroppingFields() {
        val title = AnimeTitle(
            id = "title-1",
            russianName = "Фрирен",
            englishName = "Frieren",
            originalName = "Sousou no Frieren",
            japaneseName = "葬送のフリーレン",
            synonyms = listOf("Frieren: Beyond Journey's End"),
            year = 2023,
            type = "TV",
            episodeCount = 28,
            posterUrl = "https://example.com/poster.jpg",
            status = "ongoing",
            description = "An elven mage travels after the hero's journey.",
            nextEpisodeAt = 1_725_000_000,
            genres = listOf("adventure", "fantasy"),
            ratings = listOf(TitleRating("source", 9.1, 1200)),
            ageRating = "PG-13",
            viewCount = 42_000,
            screenshots = listOf("https://example.com/screenshot.jpg"),
            trailer = AnimeTrailerTitle(
                id = "trailer-1",
                site = "youtube",
                thumbnailUrl = "https://example.com/thumb.jpg",
                sourceUrl = "https://youtube.com/watch?v=1",
            ),
            sourceMaterial = "manga",
            studios = listOf("Madhouse"),
            mainCharacters = listOf(CharacterTitle("fern", "Fern", "https://example.com/fern.jpg")),
            similarAnime = listOf(related("similar-1")),
            franchiseAnime = listOf(related("franchise-1")),
            relatedAnime = listOf(related("related-1")),
            season = 1,
            availableEpisodeCount = 27,
            posterFallbackUrl = "https://fallback.example.com/poster.jpg",
        )

        val restored = AnimeTitleRuntimePayloadCodec.decodeDetails(
            AnimeTitleRuntimePayloadCodec.encodeDetails(title),
        )

        assertEquals(title, restored)
    }

    @Test
    fun searchResponseUsesItemsEnvelope() {
        val title = AnimeTitle(
            id = "title-1",
            russianName = null,
            englishName = "Title",
            originalName = "Title",
            japaneseName = null,
            synonyms = emptyList(),
            year = null,
            type = null,
            episodeCount = null,
            posterUrl = null,
            status = null,
            description = null,
        )

        val restored = AnimeTitleRuntimePayloadCodec.decodeSearch(
            AnimeTitleRuntimePayloadCodec.encodeSearch(listOf(title)),
        )

        assertEquals(listOf(title), restored)
    }

    @Test
    fun playbackPayloadsRoundTripEpisodesLinksAndHeaders() {
        val groups = listOf(
            PlaybackGroup(
                id = "group-1",
                title = "Dub",
                qualityLabel = "Original",
                episodes = listOf(Episode("episode-1", 1.0, "Episode 1")),
            ),
        )
        val links = listOf(
            PlayerLink(
                url = "https://example.com/video.m3u8",
                type = PlayerType.DIRECT_HLS,
                quality = "1080p",
                headers = mapOf("Referer" to "https://example.com/"),
                playerName = "Player",
                translation = "Dub",
                segments = listOf(VideoSegment(VideoSegmentType.OPENING, 0, 30_000)),
                videoId = 42,
            ),
        )

        assertEquals(groups, AnimeTitleRuntimePayloadCodec.decodePlaybackGroups(
            AnimeTitleRuntimePayloadCodec.encodePlaybackGroups(groups),
        ))
        assertEquals(links, AnimeTitleRuntimePayloadCodec.decodePlayerLinks(
            AnimeTitleRuntimePayloadCodec.encodePlayerLinks(links),
        ))
    }

    @Test
    fun missingCollectionFieldIsRejected() {
        assertFailsWith<IllegalStateException> {
            AnimeTitleRuntimePayloadCodec.decodeDetails(
                buildJsonObject {
                    put("id", "title-1")
                    put("originalName", "Title")
                },
            )
        }
    }

    private fun related(id: String) = RelatedAnimeTitle(
        id = id,
        title = id,
        posterUrl = "https://example.com/$id.jpg",
        type = "OVA",
        year = 2024,
        episodeCount = 2,
        status = "released",
    )
}
