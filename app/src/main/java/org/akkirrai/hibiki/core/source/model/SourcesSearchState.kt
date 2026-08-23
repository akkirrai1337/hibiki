package org.akkirrai.hibiki.core.source

import org.akkirrai.hibiki.catalog.model.Anime

data class SourceSearchSectionState<T>(
    val sourceId: String,
    val sourceName: String,
    val items: List<T> = emptyList(),
    val hasError: Boolean = false,
    val isLoading: Boolean = false,
)

data class SourcesSearchUiState(
    val query: String = "",
    val sections: List<SourceSearchSectionState<Anime>> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
)

fun shouldRestrictSourceSearchToRussian(query: String): Boolean =
    query.any { it in '\u0400'..'\u052F' }

const val SOURCE_SEARCH_MIN_QUERY_LENGTH = 3
const val SOURCE_SEARCH_DEBOUNCE_MS = 400L
const val SOURCE_SEARCH_RESULTS_PER_SOURCE = 12
