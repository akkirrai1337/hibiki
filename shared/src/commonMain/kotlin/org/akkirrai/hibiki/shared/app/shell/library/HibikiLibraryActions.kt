package org.akkirrai.hibiki.shared.app.shell.library

import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.presentation.LibraryPresenter
import org.akkirrai.hibiki.shared.library.state.LibrarySearchFilters

internal class HibikiLibraryActions(
    presenter: LibraryPresenter,
) {
    val onCategorySelected: (LibraryCategory) -> Unit = presenter::selectCategory
    val onSearchQueryChange: (String) -> Unit = presenter::onSearchQueryChange
    val onSearchClear: () -> Unit = presenter::clearSearch
    val onFiltersApply: (LibrarySearchFilters) -> Unit = presenter::applySearchFilters
}
