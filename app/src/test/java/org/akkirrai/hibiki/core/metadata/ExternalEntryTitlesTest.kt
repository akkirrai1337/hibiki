package org.akkirrai.hibiki.core.metadata

import org.akkirrai.beakokit.metadata.ExternalMetadata
import org.akkirrai.beakokit.metadata.MetadataProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalEntryTitlesTest {
    @Test
    fun `external entry id round trips without colliding with source ids`() {
        val id = externalEntryId(MetadataProviderId.KITSU, 7442)

        assertEquals("hibiki-entry:kitsu:7442", id)
        assertEquals(MetadataProviderId.KITSU to 7442, decodeExternalEntryId(id))
        assertNull(decodeExternalEntryId("initial-d-1"))
        assertNull(decodeExternalEntryId("hibiki-entry:unknown:7442"))
        assertNull(decodeExternalEntryId("hibiki-entry:kitsu:not-a-number"))
    }

    @Test
    fun `catalog card uses preferred title and only provider-owned metadata`() {
        val anime = ExternalMetadata(
            provider = MetadataProviderId.ANILIST,
            externalId = 154587,
            romajiName = "Sousou no Frieren",
            englishName = "Frieren: Beyond Journey's End",
            year = 2023,
            type = "tv",
            status = "finished",
            episodeCount = 28,
            score = 8.9,
            scoreVotes = 1234,
        ).toCatalogAnime(preferEnglish = true)

        assertEquals("Frieren: Beyond Journey's End", anime.title)
        assertEquals("2023 · TV", anime.subtitle)
        assertEquals("28 episodes total", anime.episodesLabel)
        assertEquals("AniList", anime.ratings.single().source)
        assertEquals(8.9, anime.ratings.single().value, 0.0)
    }
}
