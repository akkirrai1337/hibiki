package org.akkirrai.hibiki.shared.app.destination.actions

import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters

internal data class AppDestinationHomeActions(
    val onQueryChange: (String) -> Unit,
    val onSearchClear: () -> Unit,
    val onFilterApply: (AnimeSearchFilters) -> Unit,
    val onSearchLoadMore: () -> Unit,
    val onSearchRetry: () -> Unit,
    val onItemVisible: (Anime) -> Unit,
    val onRefresh: () -> Unit,
)
