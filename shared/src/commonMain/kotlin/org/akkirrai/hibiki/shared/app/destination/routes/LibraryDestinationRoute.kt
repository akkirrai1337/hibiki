package org.akkirrai.hibiki.shared.app.destination.routes

import org.akkirrai.hibiki.shared.app.destination.*

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.LibraryEntry
import org.akkirrai.hibiki.shared.library.screen.LibraryScreen
import org.akkirrai.hibiki.shared.library.state.LibraryUiState
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

@Composable
internal fun ColumnScope.LibraryDestinationRoute(
    entries: List<LibraryEntry>,
    sources: List<AppSourceDescriptor>,
    state: LibraryUiState,
    onAnimeClick: (Anime) -> Unit,
    onCategorySelected: (LibraryCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onFiltersApply: (org.akkirrai.hibiki.shared.library.state.LibrarySearchFilters) -> Unit,
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
