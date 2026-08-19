package org.akkirrai.hibiki.home
import org.akkirrai.hibiki.home.data.*
import org.akkirrai.hibiki.home.model.*
import org.akkirrai.hibiki.home.presentation.*
import org.akkirrai.hibiki.home.screen.*
import org.akkirrai.hibiki.home.state.*
import org.akkirrai.hibiki.home.ui.*

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.catalog.model.Anime

class AnimeDescriptionResolverTest {
    @Test
    fun fillsOnlyMissingDescriptions() {
        val existing = anime("one", "Existing")
        val missing = anime("two", null)

        val result = listOf(existing, missing).mergeMissingDescriptions(
            mapOf("one" to "Updated", "two" to "Filled"),
        )

        assertEquals("Existing", result[0].description)
        assertEquals("Filled", result[1].description)
    }

    @Test
    fun appliesOnlyMatchingUpdates() {
        val result = listOf(anime("one", "Old")).applyDescriptionUpdates(
            mapOf("one" to anime("one", "New")),
        )

        assertEquals("New", result.single().description)
    }

    @Test
    fun preservesDescriptionsWhenHomeStateIsReloaded() {
        val previous = HomeUiState(featuredAnime = listOf(anime("one", "Loaded")))
        val refreshed = HomeUiState(featuredAnime = listOf(anime("one", null)))

        val result = refreshed.preserveLoadedDescriptions(previous)

        assertEquals("Loaded", result.featuredAnime.single().description)
    }

    private fun anime(id: String, description: String?) = Anime(
        id = id,
        title = id,
        subtitle = "",
        episodesLabel = "",
        status = "",
        description = description,
    )
}
