package org.akkirrai.hibiki.shared.library.screen
import org.akkirrai.hibiki.shared.library.*

import org.akkirrai.hibiki.shared.library.state.LibraryUiState

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun AppLibraryEntriesContent(
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
        if (state.entries.isEmpty()) {
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
