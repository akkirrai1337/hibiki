package org.akkirrai.hibiki.shared.app.destination.actions

import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.state.LibrarySearchFilters

internal data class AppDestinationLibraryActions(
    val onCategorySelected: (LibraryCategory) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onSearchClear: () -> Unit,
    val onFiltersApply: (LibrarySearchFilters) -> Unit,
    val onFilterOpen: () -> Unit,
    val onFilterVisibilityChange: (Boolean) -> Unit,
)
