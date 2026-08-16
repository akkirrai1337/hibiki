package org.akkirrai.hibiki.shared.app.destination.library

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.LibraryEntry
import org.akkirrai.hibiki.shared.library.presentation.LibraryPresenter
import org.akkirrai.hibiki.shared.library.screen.LibraryScreen
import org.akkirrai.hibiki.shared.library.state.LibrarySearchFilters
import org.akkirrai.hibiki.shared.library.state.LibraryUiState
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

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

@Composable
internal fun ColumnScope.LibraryDestinationRoute(
    entries: List<LibraryEntry>,
    sources: List<AppSourceDescriptor>,
    state: LibraryUiState,
    onAnimeClick: (Anime) -> Unit,
    onCategorySelected: (LibraryCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onFiltersApply: (LibrarySearchFilters) -> Unit,
    filterOverlayOpen: Boolean,
    onFilterOpen: () -> Unit,
    onFilterVisibilityChange: (Boolean) -> Unit,
    languageMode: LanguageMode,
    systemLanguage: String,
    bottomContentPadding: Dp,
) {
    LibraryScreen(
        entries = entries,
        sources = sources,
        state = state,
        onAnimeClick = onAnimeClick,
        onCategorySelected = onCategorySelected,
        onSearchQueryChange = onSearchQueryChange,
        onSearchClear = onSearchClear,
        onFiltersApply = onFiltersApply,
        filterOverlayOpen = filterOverlayOpen,
        onFilterOpen = onFilterOpen,
        onFilterVisibilityChange = onFilterVisibilityChange,
        languageMode = languageMode,
        systemLanguage = systemLanguage,
        bottomContentPadding = bottomContentPadding,
    )
}
