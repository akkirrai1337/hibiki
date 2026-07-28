package org.akkirrai.hibiki.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.distinctUntilChanged
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppLoadMoreState
import org.akkirrai.hibiki.shared.design.component.AppPosterPlaceholder
import org.akkirrai.hibiki.shared.library.LibraryStatusPosterFooter
import org.akkirrai.hibiki.core.design.component.AppFloatingHeader
import org.akkirrai.hibiki.shared.design.component.appVerticalAnimeListContent
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.shared.library.icon
import org.akkirrai.hibiki.core.source.labelResId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.shared.model.buildCardMeta
import org.akkirrai.hibiki.shared.home.AppRecentUpdatesContentList

@Composable
fun RecentUpdatesScreen(
    viewModel: HomeViewModel,
    onBackClick: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val libraryStatusByAnimeId = rememberLibraryStatusByAnimeId()
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        if (state.recentlyUpdated.isEmpty()) viewModel.refresh()
    }
    LaunchedEffect(
        listState,
        state.recentlyUpdated.size,
        state.isRecentUpdatesLoadingMore,
        state.canLoadMoreRecentUpdates,
    ) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.distinctUntilChanged().collect { lastVisibleIndex ->
            if (
                state.recentlyUpdated.isNotEmpty() &&
                lastVisibleIndex >= state.recentlyUpdated.lastIndex - RECENT_UPDATES_PREFETCH_DISTANCE
            ) {
                viewModel.loadMoreRecentUpdates()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        org.akkirrai.hibiki.shared.design.component.AppContentState(
            isLoading = state.isLoading,
            hasContent = state.recentlyUpdated.isNotEmpty(),
            errorMessage = state.errorMessage,
            errorTitle = stringResource(R.string.home_error_title),
            retryLabel = stringResource(R.string.search_retry),
            onRetry = viewModel::refresh,
        ) {
            AppRecentUpdatesContentList(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                appVerticalAnimeListContent(
                    items = state.recentlyUpdated,
                    metaText = { anime ->
                        anime.buildCardMeta(
                            announcementLabel = stringResource(R.string.anime_meta_announcement),
                            movieLabel = stringResource(R.string.anime_meta_movie),
                        )
                    },
                    onAnimeClick = onAnimeClick,
                    modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding),
                    posterContent = { anime ->
                        PosterImage(
                            primaryUrl = anime.posterUrl,
                            fallbackUrl = anime.posterFallbackUrl,
                            contentDescription = anime.title,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                AppPosterPlaceholder(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f),
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Outlined.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    },
                    posterFooterContent = { anime ->
                        libraryStatusByAnimeId[anime.id]?.let { category ->
                            LibraryStatusPosterFooter(
                                label = stringResource(category.labelResId),
                                icon = category.icon(),
                            )
                        }
                    },
                )
                if (state.isRecentUpdatesLoadingMore) {
                    item(key = "recent_updates_loading_more") {
                        AppLoadMoreState(
                            isLoading = true,
                            errorMessage = null,
                            errorIcon = Icons.Outlined.WarningAmber,
                            onRetry = viewModel::loadMoreRecentUpdates,
                        )
                    }
                }
            }
        }
        AppFloatingHeader(
            title = stringResource(R.string.home_recent_updates),
            onBackClick = onBackClick,
            modifier = Modifier,
            includeStatusBarsPadding = false,
        )
    }
}

private const val RECENT_UPDATES_PREFETCH_DISTANCE = 4
