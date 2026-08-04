package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
    fun `player link decoder rejects unsafe URL and headers`() {
        assertFailsWith<IllegalArgumentException> {
            AnimeTitleRuntimePayloadCodec.decodePlayerLinks(
                AnimeTitleRuntimePayloadCodec.encodePlayerLinks(
                    listOf(
                        PlayerLink(
                            url = "video\n.m3u8",
                            type = PlayerType.DIRECT_HLS,
                            quality = null,
                        ),
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AnimeTitleRuntimePayloadCodec.decodePlayerLinks(
                AnimeTitleRuntimePayloadCodec.encodePlayerLinks(
                    listOf(
                        PlayerLink(
                            url = "https://example.com/video.m3u8",
                            type = PlayerType.DIRECT_HLS,
                            quality = null,
                            headers = mapOf("X-Test" to "value\r\nInjected: yes"),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun decoderRejectsInvalidPlaybackNumbersAndSegments() {
        assertFailsWith<IllegalArgumentException> {
            AnimeTitleRuntimePayloadCodec.decodePlaybackGroups(
                buildJsonObject {
                    putJsonArray("groups") {
                        add(buildJsonObject {
                            put("id", "group-1")
                            put("title", "Dub")
                            putJsonArray("episodes") {
                                add(buildJsonObject {
                                    put("id", "episode-1")
                                    put("number", "NaN")
                                })
                            }
                        })
                    }
                },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AnimeTitleRuntimePayloadCodec.decodePlayerLinks(
                buildJsonObject {
                    putJsonArray("links") {
                        add(buildJsonObject {
                            put("url", "https://example.com/video.m3u8")
                            put("type", PlayerType.DIRECT_HLS.name)
                            putJsonArray("segments") {
                                add(buildJsonObject {
                                    put("type", VideoSegmentType.OPENING.name)
                                    put("startMs", 30_000)
                                    put("endMs", 30_000)
                                })
                            }
                        })
                    }
                },
            )
        }
    }

    @Test
    fun playerLinkDecoderRejectsNonHttpUrls() {
        assertFailsWith<IllegalArgumentException> {
            AnimeTitleRuntimePayloadCodec.decodePlayerLinks(
                buildJsonObject {
                    putJsonArray("links") {
                        add(buildJsonObject {
                            put("url", "ftp://example.com/video.m3u8")
                            put("type", PlayerType.DIRECT_HLS.name)
                        })
                    }
                },
            )
        }
    }

    @Test
    fun titleDecoderRejectsInvalidIdentityAndDisplayName() {
        val encoded = AnimeTitleRuntimePayloadCodec.encodeDetails(
            AnimeTitle(
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
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            AnimeTitleRuntimePayloadCodec.decodeDetails(
                buildJsonObject {
                    encoded.forEach { (key, value) -> put(key, value) }
                    put("id", "title\n-1")
                },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AnimeTitleRuntimePayloadCodec.decodeDetails(
                buildJsonObject {
                    encoded.forEach { (key, value) -> put(key, value) }
                    put("englishName", "")
                    put("originalName", "")
                },
            )
        }
    }

    @Test
    fun titleDecoderRejectsInvalidNestedMetadata() {
        val title = AnimeTitleRuntimePayloadCodec.encodeDetails(
            AnimeTitle(
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
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            AnimeTitleRuntimePayloadCodec.decodeDetails(
                buildJsonObject {
                    title.forEach { (key, value) -> put(key, value) }
                    putJsonArray("ratings") {
                        add(buildJsonObject {
                            put("source", "test")
                            put("value", "NaN")
                        })
                    }
                },
            )
        }
    }

    @Test
    fun missingCollectionFieldIsRejected() {
        val error = assertFailsWith<SourceRuntimePayloadException> {
            AnimeTitleRuntimePayloadCodec.decodeDetails(
                buildJsonObject {
                    put("id", "title-1")
                    put("originalName", "Title")
                },
            )
        }
        assertEquals(SourceErrorCode.INVALID_RESPONSE, error.code)
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
