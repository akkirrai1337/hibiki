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

        assertEquals(
            "TV | 2025",
            anime.buildCardMeta(
                announcementLabel = "Announced",
                separator = " | ",
            ),
        )
    }

    @Test
    fun `buildCardMeta normalizes anime type before year`() {
        val anime = anime(subtitle = "Short Movie | 2024")

        assertEquals(
            "MOVIE | 2024",
            anime.buildCardMeta(
                announcementLabel = "Announced",
                separator = " | ",
            ),
        )
    }

    @Test
    fun `buildLibraryMeta appends episodes after subtitle parts`() {
        val anime = anime(
            subtitle = "Movie | 2024",
            episodesLabel = "1 episode",
        )

        assertEquals("Movie • 2024", anime.buildLibraryMeta())
    }

    @Test
    fun `isAnnouncement recognizes transliterated status variants`() {
        assertTrue(anime(status = "anons").isAnnouncement())
    }

    @Test
    fun `youtube trailer exposes playable url`() {
        val trailer = AnimeTrailer(
            id = "abc123",
            site = "YouTube",
            thumbnailUrl = "https://example.com/trailer.jpg",
        )

        assertEquals("https://www.youtube.com/watch?v=abc123", trailer.playbackUrl)
    }

    @Test
    fun `unsupported trailer site has no playable url`() {
        val trailer = AnimeTrailer(id = "abc123", site = "unsupported")

        assertEquals(null, trailer.playbackUrl)
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
