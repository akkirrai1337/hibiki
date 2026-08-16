package org.akkirrai.hibiki.shared.home
import org.akkirrai.hibiki.shared.home.data.*
import org.akkirrai.hibiki.shared.home.model.*
import org.akkirrai.hibiki.shared.home.presentation.*
import org.akkirrai.hibiki.shared.home.screen.*
import org.akkirrai.hibiki.shared.home.state.*
import org.akkirrai.hibiki.shared.home.ui.*

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.catalog.model.Anime

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
