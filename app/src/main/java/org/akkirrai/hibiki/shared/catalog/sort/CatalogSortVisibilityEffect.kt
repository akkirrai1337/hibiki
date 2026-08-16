package org.akkirrai.hibiki.shared.catalog.sort

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect

@Composable
fun AppCatalogSortVisibilityEffect(
    listState: LazyListState,
    onVisibilityChange: (Boolean) -> Unit,
) {
    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (currentIndex, currentOffset) ->
            val isScrollingDown = currentIndex > previousIndex ||
                (currentIndex == previousIndex && currentOffset > previousOffset)
            val isScrollingUp = currentIndex < previousIndex ||
                (currentIndex == previousIndex && currentOffset < previousOffset)
            when {
                isScrollingDown -> onVisibilityChange(false)
                isScrollingUp -> onVisibilityChange(true)
            }
            previousIndex = currentIndex
            previousOffset = currentOffset
        }
    }
}
