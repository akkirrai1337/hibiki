package org.akkirrai.hibiki.search.model

import org.akkirrai.hibiki.catalog.model.Anime

data class AnimeSearchFilters(
    val sortAlias: String = "relevance",
    val typeAlias: String? = null,
    val statusAlias: String? = null,
    val includedGenreAliases: Set<String> = emptySet(),
    val excludedGenreAliases: Set<String> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
) {
    fun hasActiveFilters(): Boolean = sortAlias != "relevance" ||
        typeAlias != null ||
        statusAlias != null ||
        includedGenreAliases.isNotEmpty() ||
        excludedGenreAliases.isNotEmpty() ||
        yearFrom != null ||
        yearTo != null
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
    data class Content(
        val items: List<Anime>,
        val canLoadMore: Boolean,
        val isLoadingMore: Boolean = false,
        val loadMoreError: String? = null,
    ) : SearchUiState
}
