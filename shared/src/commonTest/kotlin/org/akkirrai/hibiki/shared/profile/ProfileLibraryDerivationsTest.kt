package org.akkirrai.hibiki.shared.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.model.Anime

class ProfileLibraryDerivationsTest {
    @Test
    fun recentItemsAreNewestDistinctTitlesLimitedToFive() {
        val data = LocalProfileData(
            library = (0 until 6).map { index ->
                LocalLibraryItem(
                    id = "id-$index",
                    anime = anime("Title ${index / 2}"),
                    categories = setOf(LibraryCategory.Watching),
                    addedAt = index.toLong(),
                )
            },
        )

        val result = data.buildRecentLibraryItems(
            statusLabel = { it.storageValue },
            dateLabel = Long::toString,
        )

        assertEquals(listOf("Title 2", "Title 1", "Title 0"), result.map { it.title })
    }

    @Test
    fun favoritesAreNewestAndLimitedToSix() {
        val data = LocalProfileData(
            library = (0 until 7).map { index ->
                LocalLibraryItem(
                    id = "favorite-$index",
                    anime = anime("Favorite $index"),
                    categories = setOf(LibraryCategory.Favorite),
                    addedAt = index.toLong(),
                )
            },
        )

        val result = data.buildFavoriteLibraryItems("favorite", Long::toString)

        assertEquals(6, result.size)
        assertEquals("Favorite 6", result.first().title)
        assertEquals("Favorite 1", result.last().title)
    }

    private fun anime(title: String) = Anime(
        id = title,
        title = title,
        subtitle = "",
        episodesLabel = "",
        status = "",
    )
}
