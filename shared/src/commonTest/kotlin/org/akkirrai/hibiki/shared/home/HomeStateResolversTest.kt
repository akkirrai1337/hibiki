package org.akkirrai.hibiki.shared.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState

class HomeStateResolversTest {
    @Test
    fun searchIsActiveForQueryOrNonIdleResult() {
        val anime = Anime(id = "1", title = "Title", subtitle = "", episodesLabel = "", status = "")
        assertTrue(HomeUiState(searchQuery = "naruto").isSearchActive)
        assertTrue(HomeUiState(searchResult = SearchUiState.Content(listOf(anime), canLoadMore = false)).isSearchActive)
        assertFalse(HomeUiState().isSearchActive)
    }

    @Test
    fun feedContentMatchesHomeLoadingGuard() {
        val anime = Anime(id = "1", title = "Title", subtitle = "", episodesLabel = "", status = "")
        assertFalse(HomeUiState().hasFeedContent)
        assertTrue(HomeUiState(continueAnime = anime).hasFeedContent)
    }
}
