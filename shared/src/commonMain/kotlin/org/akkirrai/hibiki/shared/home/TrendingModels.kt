package org.akkirrai.hibiki.shared.home

import org.akkirrai.hibiki.shared.model.Anime

enum class TrendingFilter(
    val typeAlias: String?,
) {
    All(typeAlias = null),
    Movies(typeAlias = "movie"),
    Ona(typeAlias = "ona"),
}

data class TrendingAnimeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedFilter: TrendingFilter = TrendingFilter.All,
    val items: List<Anime> = emptyList(),
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadMoreError: String? = null,
)
