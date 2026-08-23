package org.akkirrai.hibiki.home
import org.akkirrai.hibiki.home.data.*
import org.akkirrai.hibiki.home.model.*
import org.akkirrai.hibiki.home.presentation.*
import org.akkirrai.hibiki.home.screen.*
import org.akkirrai.hibiki.home.state.*
import org.akkirrai.hibiki.home.ui.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.library.LibraryEntry
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

    private fun anime(id: String) = Anime(id = id, title = id, subtitle = "", episodesLabel = "", status = "")

    @Test
    fun resolvedStateAlwaysAdoptsSearchFields() {
        val search = HomeSearchUiState(
            query = "naruto",
            result = SearchUiState.Content(listOf(anime("s1")), canLoadMore = false),
            isFilterCatalogLoading = true,
        )
        val resolved = resolveHomeUiState(
            baseState = HomeUiState(),
            libraryEntries = emptyList(),
            searchState = search,
        )
        assertEquals(search.query, resolved.searchQuery)
        assertEquals(search.result, resolved.searchResult)
        assertEquals(search.filterCatalog, resolved.searchFilterCatalog)
        assertEquals(search.isFilterCatalogLoading, resolved.isSearchFilterCatalogLoading)
        assertEquals(search.filters, resolved.searchFilters)
    }

    @Test
    fun recentlyAddedToLibraryFallsBackToLibraryEntriesWhenFeedHasNone() {
        val resolved = resolveHomeUiState(
            baseState = HomeUiState(recentlyAddedToLibrary = emptyList()),
            libraryEntries = listOf(
                LibraryEntry(anime = anime("watching"), category = LibraryCategory.Watching),
                LibraryEntry(anime = anime("saved"), category = LibraryCategory.Saved),
                LibraryEntry(anime = anime("recent"), category = LibraryCategory.Recent),
            ),
            searchState = HomeSearchUiState(),
        )
        assertEquals(listOf(anime("watching")), resolved.recentlyAddedToLibrary)
    }

    @Test
    fun recentlyAddedToLibraryKeepsFeedValueWhenAlreadyPresent() {
        val feedProvided = listOf(anime("from-feed"))
        val resolved = resolveHomeUiState(
            baseState = HomeUiState(recentlyAddedToLibrary = feedProvided),
            libraryEntries = listOf(LibraryEntry(anime = anime("from-library"), category = LibraryCategory.Watching)),
            searchState = HomeSearchUiState(),
        )
        assertEquals(feedProvided, resolved.recentlyAddedToLibrary)
    }
}
