package org.akkirrai.hibiki.shared.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.model.Anime

/** Shared production Catalog composition; sort/filter surfaces are explicit host slots. */
@Composable
fun AppCatalogScreen(
    state: AnimeCatalogUiState,
    listState: LazyListState,
    onAnimeClick: (Anime) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    errorTitle: String,
    retryLabel: String,
    errorIcon: ImageVector,
    announcementLabel: String,
    movieLabel: String,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    posterContent: @Composable BoxScope.(Anime) -> Unit,
    posterFooterContent: @Composable (Anime) -> Unit,
    onItemVisible: (Anime) -> Unit,
    topScrimContent: @Composable BoxScope.() -> Unit,
    headerContent: @Composable BoxScope.() -> Unit,
    overlayContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        AppCatalogResultsContent(
            state = state,
            listState = listState,
            onAnimeClick = onAnimeClick,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            errorTitle = errorTitle,
            retryLabel = retryLabel,
            errorIcon = errorIcon,
            announcementLabel = announcementLabel,
            movieLabel = movieLabel,
            topContentPadding = topContentPadding,
            bottomContentPadding = bottomContentPadding,
            posterContent = posterContent,
            posterFooterContent = posterFooterContent,
            onItemVisible = onItemVisible,
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            content = topScrimContent,
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            content = headerContent,
        )
        overlayContent()
    }
}
