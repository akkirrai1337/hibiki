package org.akkirrai.hibiki.feature.details

import org.junit.Assert.assertEquals
import org.junit.Test
import org.akkirrai.hibiki.shared.details.model.formatRelatedAnimeMetadata
import org.akkirrai.hibiki.shared.details.model.extractNextEpisodeNumber

class DetailsScreenLogicTest {
    @Test
    fun `next episode number follows released episode count`() {
        assertEquals(12, extractNextEpisodeNumber("11 of 24 episodes"))
        assertEquals(4, extractNextEpisodeNumber("3 серии вышло"))
        assertEquals(null, extractNextEpisodeNumber("Не выбрано"))
    }

    @Test
    fun `related metadata combines year and type`() {
        assertEquals("2022 • TV", formatRelatedAnimeMetadata(2022, "tv"))
        assertEquals("2022 • ONA", formatRelatedAnimeMetadata(2022, "ona"))
        assertEquals("OVA", formatRelatedAnimeMetadata(null, "ova"))
        assertEquals(
            "анонс • TV",
            formatRelatedAnimeMetadata(0, "tv", "announcement", "анонс"),
        )
        assertEquals("TV", formatRelatedAnimeMetadata(0, "tv", "unknown", "анонс"))
    }
}
