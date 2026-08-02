package org.akkirrai.hibiki.shared.catalog

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.shared.design.component.AppLoadMoreState

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
