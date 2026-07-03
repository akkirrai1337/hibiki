package org.akkirrai.hibiki.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimePresentationTest {
    @Test
    fun `buildCardMeta returns announcement label for announcement titles`() {
        val anime = anime(
            subtitle = "TV | 2025",
            episodesLabel = "12 episodes",
            status = "announcement",
        )

        assertEquals("Announced", anime.buildCardMeta(announcementLabel = "Announced"))
    }

    @Test
    fun `buildCardMeta prefers cleaned subtitle over episodes label`() {
        val anime = anime(
            subtitle = "TV | Unknown | 2025",
            episodesLabel = "24 episodes",
        )

        assertEquals("TV • 2025", anime.buildCardMeta(announcementLabel = "Announced"))
    }

    @Test
    fun `buildLibraryMeta appends episodes after subtitle parts`() {
        val anime = anime(
            subtitle = "Movie | 2024",
            episodesLabel = "1 episode",
        )

        assertEquals("Movie • 2024 • 1 episode", anime.buildLibraryMeta())
    }

    @Test
    fun `isAnnouncement recognizes transliterated status variants`() {
        assertTrue(anime(status = "anons").isAnnouncement())
    }

    private fun anime(
        subtitle: String = "",
        episodesLabel: String = "",
        status: String = "",
    ) = Anime(
        id = "1",
        title = "Test",
        subtitle = subtitle,
        episodesLabel = episodesLabel,
        status = status,
    )
}
