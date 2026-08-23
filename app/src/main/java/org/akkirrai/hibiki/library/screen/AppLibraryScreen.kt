package org.akkirrai.hibiki.library.screen
import org.akkirrai.hibiki.library.*
import org.akkirrai.hibiki.library.ui.resolveLibraryEmptyStateText

import org.akkirrai.hibiki.library.state.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.design.component.state.AppMessageState

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
    filterVisible: Boolean,
    onFilterVisibilityChange: (Boolean) -> Unit,
    filterContent: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val categoryEntryCount = state.categoryCounts[state.selectedCategory] ?: 0
    val filterAvailable = categoryEntryCount >= LIBRARY_FILTER_MINIMUM_ENTRY_COUNT
    LaunchedEffect(filterAvailable) {
        if (!filterAvailable) onFilterVisibilityChange(false)
    }

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
                            onFilterVisibilityChange(true)
                        },
                        showFilterButton = filterAvailable,
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

    if (filterVisible && filterAvailable && filterContent != null) {
        filterContent { onFilterVisibilityChange(false) }
    }
}

private const val LIBRARY_FILTER_MINIMUM_ENTRY_COUNT = 2

@Composable
private fun AppLibraryEntriesContent(
    state: LibraryUiState,
    bottomContentPadding: Dp,
    onEntryClick: (LibraryEntry) -> Unit,
    emptyContent: @Composable (filtered: Boolean) -> Unit,
    entryContent: @Composable (LibraryEntry, Modifier) -> Unit,
    headerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var visibleLimit by remember { mutableIntStateOf(LIBRARY_PAGE_SIZE) }
    val visibleEntries = state.visibleEntries

    LaunchedEffect(state.selectedCategory, state.searchQuery, state.searchFilters) {
        visibleLimit = LIBRARY_PAGE_SIZE
        listState.scrollToItem(0)
    }

    LaunchedEffect(listState, visibleEntries.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= layoutInfo.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (visibleLimit < visibleEntries.size) {
                    visibleLimit = (visibleLimit + LIBRARY_PAGE_SIZE).coerceAtMost(visibleEntries.size)
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(LibraryEntriesRowGap),
    ) {
        item { headerContent() }
        if (state.isRefreshing && state.entries.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(
                        Modifier.size(LibraryEntriesRefreshIndicatorSize),
                        strokeWidth = LibraryEntriesRefreshIndicatorStrokeWidth,
                    )
                }
            }
        }
        // Recent is a hidden bookkeeping flag (auto-assigned the moment playback starts) --
        // its presence alone shouldn't make the library look "filtered" instead of genuinely
        // empty when every visible category has nothing in it.
        val hasVisibleEntries = state.entries.any { it.category != LibraryCategory.Recent }
        if (!hasVisibleEntries) {
            item { emptyContent(false) }
        } else if (visibleEntries.isEmpty()) {
            item { emptyContent(true) }
        } else {
            items(visibleEntries.take(visibleLimit).chunked(2), key = { row -> row.first().anime.id }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LibraryEntriesHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(LibraryEntriesItemGap),
                    verticalAlignment = Alignment.Top,
                ) {
                    row.forEach { entry ->
                        entryContent(entry, Modifier.weight(1f))
                    }
                    if (row.size == 1) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private const val LIBRARY_PAGE_SIZE = 12

@Composable
private fun AppLibraryEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    iconContent: @Composable (Modifier) -> Unit = { iconModifier ->
        Icon(
            imageVector = Icons.Outlined.BookmarkBorder,
            contentDescription = null,
            modifier = iconModifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
) {
    LibraryEmptyState(
        title = title,
        message = message,
        modifier = modifier.padding(horizontal = UiDimens.ScreenPadding),
        iconContent = {
            Box(
                modifier = Modifier
                    .size(LibraryEmptyStateIconContainerSize)
                    .clip(RoundedCornerShape(LibraryEmptyStateIconCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                iconContent(Modifier.size(LibraryEmptyStateIconSize))
            }
        },
    )
}

@Composable
private fun LibraryEmptyState(
    title: String,
    message: String,
    iconContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppMessageState(
        title = title,
        message = message,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = LibraryEmptyStateTopPadding),
        titleStyle = MaterialTheme.typography.titleLarge,
        messageModifier = Modifier.padding(
            top = LibraryEmptyStateMessageTopPadding,
            start = LibraryEmptyStateMessageHorizontalPadding,
            end = LibraryEmptyStateMessageHorizontalPadding,
        ),
        messageMaxLines = 2,
        messageOverflow = TextOverflow.Ellipsis,
        iconSlot = { iconContent(Modifier) },
    )
}
