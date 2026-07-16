package org.akkirrai.hibiki.feature.home

import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.model.SearchUiState

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
    val searchFilterCatalog: AnimeSearchFilterCatalog? = null,
    val isSearchFilterCatalogLoading: Boolean = false,
    val searchFilters: AnimeSearchFilters = AnimeSearchFilters(),
)
