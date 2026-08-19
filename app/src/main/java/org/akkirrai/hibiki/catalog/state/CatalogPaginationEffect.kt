package org.akkirrai.hibiki.catalog.state

import org.akkirrai.hibiki.catalog.*
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogUiState

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

@Composable
fun AppCatalogPaginationEffect(
    listState: LazyListState,
    state: AnimeCatalogUiState,
    onLoadMore: () -> Unit,
) {
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val isNearEnd = lastVisibleItem >= layoutInfo.totalItemsCount - CATALOG_SCROLL_THRESHOLD
            isNearEnd &&
                !latestState.isLoading &&
                !latestState.isLoadingMore &&
                latestState.canLoadMore &&
                latestState.error == null
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) onLoadMore()
        }
    }
}

private const val CATALOG_SCROLL_THRESHOLD = 3
