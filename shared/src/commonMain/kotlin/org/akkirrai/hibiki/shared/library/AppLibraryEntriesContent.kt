package org.akkirrai.hibiki.shared.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { headerContent() }
        if (state.isRefreshing && state.entries.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        if (state.entries.isEmpty()) {
            item { emptyContent(false) }
        } else if (state.visibleEntries.isEmpty()) {
            item { emptyContent(true) }
        } else {
            items(state.visibleEntries, key = { it.anime.id }) { entry ->
                entryContent(entry, Modifier)
            }
        }
    }
}
