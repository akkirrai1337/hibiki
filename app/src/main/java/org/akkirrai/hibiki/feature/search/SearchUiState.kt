package org.akkirrai.hibiki.feature.search

import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.model.SearchUiState

data class SearchScreenState(
    val query: String = "",
    val filters: AnimeSearchFilters = AnimeSearchFilters(),
    val filterCatalog: AnimeSearchFilterCatalog? = null,
    val isFilterCatalogLoading: Boolean = false,
    val result: SearchUiState = SearchUiState.Idle,
)
