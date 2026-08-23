package org.akkirrai.hibiki.catalog.state

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogUiState
import org.akkirrai.hibiki.design.component.state.AppContentState
import org.akkirrai.hibiki.design.component.state.AppLoadMoreState

@Composable
fun AppCatalogContentState(
    isLoading: Boolean,
    hasContent: Boolean,
    errorMessage: String?,
    errorTitle: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AppContentState(
        isLoading = isLoading,
        hasContent = hasContent,
        errorMessage = errorMessage,
        errorTitle = errorTitle,
        retryLabel = retryLabel,
        onRetry = onRetry,
        errorIcon = Icons.Outlined.WarningAmber,
        errorIconTint = MaterialTheme.colorScheme.error,
        modifier = modifier,
        content = content,
    )
}

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

fun LazyListScope.appCatalogPaginationStates(
    isLoadingMore: Boolean,
    errorMessage: String?,
    errorIcon: ImageVector,
    onRetry: () -> Unit,
) {
    if (isLoadingMore) {
        item(key = "catalog_loading_more") {
            AppLoadMoreState(
                isLoading = true,
                errorMessage = null,
                errorIcon = errorIcon,
                onRetry = onRetry,
            )
        }
    }

    if (isLoadingMore && errorMessage != null) {
        item(key = "catalog_load_more_error") {
            AppLoadMoreState(
                isLoading = false,
                errorMessage = errorMessage,
                errorIcon = errorIcon,
                onRetry = onRetry,
            )
        }
    }
}
