package org.akkirrai.hibiki.shared.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.model.Anime

data class AppLibraryScreenLabels(
    val searchPlaceholder: String,
    val filterContentDescription: String,
    val clearContentDescription: String,
    val categoryLabels: Map<LibraryCategory, String>,
    val emptyTitle: String,
    val emptyMessage: String,
    val filteredTitle: String,
    val searchTitle: String,
    val filteredMessage: String,
    val categoryEmptyLabels: Map<LibraryCategory, String>,
    val announcementLabel: String,
    val movieLabel: String,
    val libraryStatusLabel: @Composable (LibraryCategory) -> String,
)

@Composable
fun AppLibraryScreen(
    state: LibraryUiState,
    labels: AppLibraryScreenLabels,
    bottomContentPadding: Dp,
    onAnimeClick: (Anime) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onFilterClick: () -> Unit,
    onCategorySelected: (LibraryCategory) -> Unit,
    entryContent: @Composable (LibraryEntry, Modifier) -> Unit,
    filterContent: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isFilterVisible by remember { mutableStateOf(false) }

    AppLibraryEntriesContent(
        state = state,
        modifier = modifier,
        bottomContentPadding = bottomContentPadding,
        onEntryClick = { entry -> onAnimeClick(entry.anime) },
        headerContent = {
            AppLibraryHeader(
                searchContent = { searchModifier ->
                    AppLibrarySearchBar(
                        query = state.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClear = onClearSearch,
                        placeholder = labels.searchPlaceholder,
                        filterContentDescription = labels.filterContentDescription,
                        clearContentDescription = labels.clearContentDescription,
                        onFilterClick = {
                            onFilterClick()
                            isFilterVisible = true
                        },
                        modifier = searchModifier,
                    )
                },
                selected = state.selectedCategory,
                categories = state.orderedCategories,
                counts = state.categoryCounts,
                label = { category -> labels.categoryLabels[category].orEmpty() },
                icon = LibraryCategory::icon,
                onSelected = onCategorySelected,
            )
        },
        emptyContent = { filtered ->
            val emptyState = resolveLibraryEmptyStateText(
                filtered = filtered,
                searchQuery = state.searchQuery,
                category = state.selectedCategory,
                emptyTitle = labels.emptyTitle,
                emptyMessage = labels.emptyMessage,
                filteredTitle = labels.filteredTitle,
                searchTitle = labels.searchTitle,
                filteredMessage = labels.filteredMessage,
                categoryLabels = labels.categoryEmptyLabels,
            )
            AppLibraryEmptyState(title = emptyState.title, message = emptyState.message)
        },
        entryContent = { entry, entryModifier -> entryContent(entry, entryModifier) },
    )

    if (isFilterVisible && filterContent != null) {
        filterContent { isFilterVisible = false }
    }
}
