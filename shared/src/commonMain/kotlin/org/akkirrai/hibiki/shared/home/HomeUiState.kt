package org.akkirrai.hibiki.shared.home

import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.model.SearchUiState

data class HomeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val featuredAnime: List<Anime> = emptyList(),
    val continueAnime: Anime? = null,
    val popular: List<Anime> = emptyList(),
    val trending: List<Anime> = emptyList(),
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
