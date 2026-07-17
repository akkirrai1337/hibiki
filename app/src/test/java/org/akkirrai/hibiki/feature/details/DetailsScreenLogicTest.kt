package org.akkirrai.hibiki.feature.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsScreenLogicTest {
    @Test
    fun `next episode countdown is limited to ongoing titles`() {
        assertTrue(isOngoingStatus("Онгоинг"))
        assertTrue(isOngoingStatus("currently airing"))
        assertFalse(isOngoingStatus("Вышел"))
    }

    @Test
    fun `next episode number follows released episode count`() {
        assertEquals(12, extractNextEpisodeNumber("11 of 24 episodes"))
        assertEquals(4, extractNextEpisodeNumber("3 серии вышло"))
        assertEquals(null, extractNextEpisodeNumber("Не выбрано"))
    }

    @Test
    fun `scheme image urls are normalized before loading`() {
        assertEquals(
            "https://static.yani.tv/poster.webp",
            "//static.yani.tv/poster.webp".toAbsoluteImageUrl(),
        )
        assertNull("/poster.webp".toAbsoluteImageUrl())
    }

    @Test
    fun `related metadata combines year and type`() {
        assertEquals("2022 • TV", formatRelatedAnimeMetadata(2022, "tv"))
        assertEquals("2022 • ONA", formatRelatedAnimeMetadata(2022, "ona"))
        assertEquals("OVA", formatRelatedAnimeMetadata(null, "ova"))
    }
}
