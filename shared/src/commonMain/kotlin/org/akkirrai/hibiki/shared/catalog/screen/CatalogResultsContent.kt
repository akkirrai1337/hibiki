package org.akkirrai.hibiki.shared.catalog.screen

import org.akkirrai.hibiki.shared.catalog.state.*
import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogUiState

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.shared.design.component.content.appVerticalAnimeListContent
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.buildCardMeta

fun LazyListScope.appCatalogResultsContent(
    items: List<Anime>,
    announcementLabel: String,
    movieLabel: String,
    onAnimeClick: (Anime) -> Unit,
    posterContent: @Composable BoxScope.(Anime) -> Unit,
    posterFooterContent: (@Composable (Anime) -> Unit)? = null,
    onItemVisible: ((Anime) -> Unit)? = null,
    isLoadingMore: Boolean,
    paginationErrorMessage: String?,
    paginationErrorIcon: ImageVector = Icons.Outlined.WarningAmber,
    onLoadMoreRetry: () -> Unit,
) {
    appVerticalAnimeListContent(
        items = items,
        metaText = { anime ->
            anime.buildCardMeta(
                announcementLabel = announcementLabel,
                movieLabel = movieLabel,
                maxSubtitleParts = 2,
                separator = " • ",
            )
        },
        onAnimeClick = onAnimeClick,
        posterContent = posterContent,
        posterFooterContent = posterFooterContent,
        onItemVisible = onItemVisible,
    )
    appCatalogPaginationStates(
        isLoadingMore = isLoadingMore,
        errorMessage = paginationErrorMessage,
        errorIcon = paginationErrorIcon,
        onRetry = onLoadMoreRetry,
    )
}
