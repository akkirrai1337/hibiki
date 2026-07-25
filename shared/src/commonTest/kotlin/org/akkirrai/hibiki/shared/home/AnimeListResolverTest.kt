package org.akkirrai.hibiki.shared.home

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.Anime

class AnimeListResolverTest {
    @Test
    fun mergesPagesWithoutChangingExistingOrder() {
        val first = anime("one", "One")
        val second = anime("two", "Two")
        val duplicate = anime("one", "Updated One")

        val result = mergeAnimePreservingOrder(listOf(first), listOf(duplicate, second))

        assertEquals(listOf("one", "two"), result.map(Anime::id))
        assertEquals("One", result.first().title)
    }

    private fun anime(id: String, title: String) = Anime(
        id = id,
        title = title,
        subtitle = "",
        episodesLabel = "",
        status = "",
    )
}
