package org.akkirrai.hibiki.home
import org.akkirrai.hibiki.home.data.*
import org.akkirrai.hibiki.home.model.*
import org.akkirrai.hibiki.home.presentation.*
import org.akkirrai.hibiki.home.screen.*
import org.akkirrai.hibiki.home.state.*
import org.akkirrai.hibiki.home.ui.*

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.search.model.SearchUiState

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
