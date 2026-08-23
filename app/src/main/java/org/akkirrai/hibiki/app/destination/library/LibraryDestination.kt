package org.akkirrai.hibiki.app.destination.library

import androidx.compose.foundation.lazy.LazyListState
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.library.presentation.LibraryPresenter
import org.akkirrai.hibiki.library.state.LibrarySearchFilters

internal class HibikiLibraryActions(
    presenter: LibraryPresenter,
) {
    val onCategorySelected: (LibraryCategory) -> Unit = presenter::selectCategory
    val onSearchQueryChange: (String) -> Unit = presenter::onSearchQueryChange
    val onSearchClear: () -> Unit = presenter::clearSearch
    val onFiltersApply: (LibrarySearchFilters) -> Unit = presenter::applySearchFilters
}

internal data class AppDestinationLibraryState(
    val libraryPresenter: LibraryPresenter,
    val filterOverlayOpen: Boolean,
    val listState: LazyListState,
)
