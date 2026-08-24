package org.akkirrai.hibiki.core.source

import org.akkirrai.hibiki.catalog.model.Anime
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimeDisplayLabelsTest {
    private fun anime(episodesLabel: String) = Anime(
        id = "test:1",
        title = "Test",
        subtitle = "",
        episodesLabel = episodesLabel,
        status = "",
    )

    @Test
    fun `re-localizes a stale English label to Russian, preserving the count`() {
        val result = anime("12 episodes").withLocalizedEpisodesLabel(preferEnglish = false)
        assertEquals("12 серий", result.episodesLabel)
    }

    @Test
    fun `re-localizes a stale Russian label to English, preserving the count`() {
        val result = anime("1 серия").withLocalizedEpisodesLabel(preferEnglish = true)
        assertEquals("1 episode", result.episodesLabel)
    }

    @Test
    fun `leaves an already-correct label as is`() {
        val result = anime("3 серии").withLocalizedEpisodesLabel(preferEnglish = false)
        assertEquals("3 серии", result.episodesLabel)
    }

    @Test
    fun `falls back to the original text when it has no episode count to re-localize`() {
        val result = anime("Announcement").withLocalizedEpisodesLabel(preferEnglish = false)
        assertEquals("Announcement", result.episodesLabel)
    }
}
