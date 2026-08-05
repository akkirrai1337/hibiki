package org.akkirrai.hibiki.shared.home
import org.akkirrai.hibiki.shared.home.data.*
import org.akkirrai.hibiki.shared.home.model.*
import org.akkirrai.hibiki.shared.home.presentation.*
import org.akkirrai.hibiki.shared.home.screen.*
import org.akkirrai.hibiki.shared.home.state.*
import org.akkirrai.hibiki.shared.home.ui.*

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.search.model.SearchUiState

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
