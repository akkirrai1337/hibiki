package org.akkirrai.hibiki.shared.source

import org.akkirrai.hibiki.shared.model.Anime

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

fun isSourceSearchActive(query: String): Boolean = query.trim().length >= SOURCE_SEARCH_MIN_QUERY_LENGTH

fun shouldRestrictSourceSearchToRussian(query: String): Boolean =
    query.any { it in '\u0400'..'\u052F' }

fun <T> List<SourceSearchSectionState<T>>.visibleSourceSearchSections(): List<SourceSearchSectionState<T>> =
    filter { section ->
        section.isLoading || section.hasError || section.items.isNotEmpty()
    }

const val SOURCE_SEARCH_MIN_QUERY_LENGTH = 3
const val SOURCE_SEARCH_DEBOUNCE_MS = 400L
const val SOURCE_SEARCH_RESULTS_PER_SOURCE = 12
