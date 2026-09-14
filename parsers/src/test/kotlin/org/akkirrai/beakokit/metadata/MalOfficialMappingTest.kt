package org.akkirrai.beakokit.metadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MalOfficialMappingTest {
    private val searchResponse = """
        {
          "data": [
            {
              "node": {
                "id": 52991,
                "title": "Sousou no Frieren",
                "main_picture": {
                  "medium": "https://cdn.myanimelist.net/images/anime/1015/138006.jpg",
                  "large": "https://cdn.myanimelist.net/images/anime/1015/138006l.jpg"
                },
                "alternative_titles": { "synonyms": ["Frieren at the Funeral", ""], "en": "Frieren: Beyond Journey's End", "ja": "葬送のフリーレン" },
                "start_date": "2023-09-29",
                "synopsis": "During their decade-long quest...",
                "mean": 9.3,
                "num_scoring_users": 612345,
                "media_type": "tv",
                "status": "finished_airing",
                "num_episodes": 28,
                "start_season": { "year": 2023, "season": "fall" },
                "genres": [{ "id": 2, "name": "Adventure" }, { "id": 46, "name": "Award Winning" }],
                "studios": [{ "id": 11, "name": "Madhouse" }],
                "nsfw": "white",
                "rating": "pg_13"
              }
            },
            {
              "node": {
                "id": 59978,
                "title": "Sousou no Frieren 2nd Season",
                "start_date": "2026",
                "media_type": "tv",
                "status": "not_yet_aired",
                "num_episodes": 0,
                "alternative_titles": { "synonyms": [], "en": "", "ja": "" }
              }
            }
          ],
          "paging": {}
        }
    """.trimIndent()

    private fun nodes() = metadataJson.decodeFromString(MalOfficialListResponse.serializer(), searchResponse)
        .data.orEmpty().mapNotNull { it.node }

    @Test
    fun `maps a full official entry onto provider-neutral metadata`() {
        val metadata = nodes().first().toExternalMetadata()

        assertEquals(MetadataProviderId.MAL, metadata.provider)
        assertEquals(52991, metadata.externalId)
        assertEquals(52991, metadata.malId)
        assertEquals("Sousou no Frieren", metadata.romajiName)
        assertEquals("Frieren: Beyond Journey's End", metadata.englishName)
        assertEquals("葬送のフリーレン", metadata.nativeName)
        assertEquals(listOf("Frieren at the Funeral"), metadata.synonyms)
        assertEquals("https://cdn.myanimelist.net/images/anime/1015/138006l.jpg", metadata.posterUrl)
        assertEquals(2023, metadata.year)
        assertEquals("tv", metadata.type)
        assertEquals("released", metadata.status)
        assertEquals(28, metadata.episodeCount)
        assertEquals(9.3, metadata.score)
        assertEquals(612345, metadata.scoreVotes)
        assertEquals("PG-13", metadata.ageRating)
        assertEquals(listOf("Adventure", "Award Winning"), metadata.genres)
        assertEquals(listOf("Madhouse"), metadata.studios)
        assertFalse(metadata.isAdult)
    }

    @Test
    fun `treats unknown counts and blank titles as absent`() {
        val metadata = nodes()[1].toExternalMetadata()

        assertNull(metadata.episodeCount)
        assertNull(metadata.englishName)
        assertNull(metadata.nativeName)
        assertNull(metadata.posterUrl)
        assertEquals(2026, metadata.year)
        assertEquals("announced", metadata.status)
    }

    @Test
    fun `an entry rated Rx is adult`() {
        val adult = MalOfficialAnime(id = 1, rating = "rx")

        assertTrue(adult.toExternalMetadata().isAdult)
    }

    @Test
    fun `match candidates carry every name the entry is known by`() {
        val candidate = nodes().first().toMatchCandidate()

        assertEquals(
            listOf("Sousou no Frieren", "Frieren: Beyond Journey's End", "葬送のフリーレン", "Frieren at the Funeral"),
            candidate.names,
        )
        assertEquals(2023, candidate.year)
    }
}
