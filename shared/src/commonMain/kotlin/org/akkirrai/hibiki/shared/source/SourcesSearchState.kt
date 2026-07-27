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
