package org.akkirrai.hibiki.app.destination.library

import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.library.LibraryEntry
import org.akkirrai.hibiki.library.presentation.LibraryPresenter
import org.akkirrai.hibiki.library.state.LibrarySearchFilters
import org.akkirrai.hibiki.library.state.LibraryUiState

internal class HibikiLibraryActions(
    presenter: LibraryPresenter,
) {
    val onCategorySelected: (LibraryCategory) -> Unit = presenter::selectCategory
    val onSearchQueryChange: (String) -> Unit = presenter::onSearchQueryChange
    val onSearchClear: () -> Unit = presenter::clearSearch
    val onFiltersApply: (LibrarySearchFilters) -> Unit = presenter::applySearchFilters
}

internal data class AppDestinationLibraryActions(
    val onCategorySelected: (LibraryCategory) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onSearchClear: () -> Unit,
    val onFiltersApply: (LibrarySearchFilters) -> Unit,
    val onFilterOpen: () -> Unit,
    val onFilterVisibilityChange: (Boolean) -> Unit,
)

internal data class AppDestinationLibraryState(
    val entries: List<LibraryEntry>,
    val ui: LibraryUiState,
    val filterOverlayOpen: Boolean,
)
