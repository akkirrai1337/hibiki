package org.akkirrai.hibiki.feature.home

import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.hibiki.core.model.Anime
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
    val recentlyUpdated: List<Anime> = emptyList(),
    val profileAvatarUrl: String? = null,
    val searchQuery: String = "",
    val searchResult: SearchUiState = SearchUiState.Idle,
    val searchFilterCatalog: AnimeSearchFilterCatalog? = null,
    val isSearchFilterCatalogLoading: Boolean = false,
    val searchFilters: HomeSearchFilters = HomeSearchFilters(),
)

data class HomeSearchFilters(
    val sortAlias: String = "relevance",
    val typeAlias: String? = null,
    val statusAlias: String? = null,
    val includedGenreAliases: Set<String> = emptySet(),
    val excludedGenreAliases: Set<String> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
) {
    fun hasActiveFilters(): Boolean {
        return sortAlias != "relevance" ||
            typeAlias != null ||
            statusAlias != null ||
            includedGenreAliases.isNotEmpty() ||
            excludedGenreAliases.isNotEmpty() ||
            yearFrom != null ||
            yearTo != null
    }
}
