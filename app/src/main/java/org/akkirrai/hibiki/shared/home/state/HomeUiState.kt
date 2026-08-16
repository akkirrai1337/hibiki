package org.akkirrai.hibiki.shared.home.state

import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.search.model.SearchUiState

data class HomeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val recentlyWatched: List<Anime> = emptyList(),
    val recentlyAddedToLibrary: List<Anime> = emptyList(),
    val featuredAnime: List<Anime> = emptyList(),
    val continueAnime: Anime? = null,
    val popular: List<Anime> = emptyList(),
    val trending: List<Anime> = emptyList(),
    val popularThisSeason: List<Anime> = emptyList(),
    val upcomingNextSeason: List<Anime> = emptyList(),
    val isTrendingLoadingMore: Boolean = false,
    val canLoadMoreTrending: Boolean = true,
    val trendingNextOffset: Int = 12,
    val isRandomLoading: Boolean = false,
    val recentlyUpdated: List<Anime> = emptyList(),
    val isRecentUpdatesLoadingMore: Boolean = false,
    val canLoadMoreRecentUpdates: Boolean = true,
    val recentUpdatesLoadMoreError: String? = null,
    val searchQuery: String = "",
    val searchResult: SearchUiState = SearchUiState.Idle,
    val searchFilterCatalog: AnimeCatalogFilterCatalog? = null,
    val isSearchFilterCatalogLoading: Boolean = false,
    val searchFilters: AnimeSearchFilters = AnimeSearchFilters(),
)
