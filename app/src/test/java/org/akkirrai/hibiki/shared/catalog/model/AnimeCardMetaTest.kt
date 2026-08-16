package org.akkirrai.hibiki.shared.catalog.model

import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.AnimeRating
import org.akkirrai.hibiki.shared.catalog.model.buildCardMeta
import kotlin.test.Test
import kotlin.test.assertEquals

class AnimeCardMetaTest {
    @Test
    fun formatsTypeYearAndRating() {
        val anime = Anime(
            id = "1",
            title = "Title",
            subtitle = "TV • 2024",
            episodesLabel = "12 episodes",
            status = "Finished",
            ratings = listOf(AnimeRating(source = "score", value = 8.26)),
        )

        assertEquals("TV • 2024 • 8.3 ★", anime.buildCardMeta(announcementLabel = "Announcement"))
    }

    @Test
    fun announcementUsesLocalizedLabel() {
        val anime = Anime(
            id = "1",
            title = "Title",
            subtitle = "",
            episodesLabel = "Анонс",
            status = "",
        )

        assertEquals("Announcement", anime.buildCardMeta(announcementLabel = "Announcement"))
    }
}
