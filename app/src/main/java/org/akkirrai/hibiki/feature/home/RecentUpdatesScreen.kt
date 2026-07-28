package org.akkirrai.hibiki.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import org.akkirrai.hibiki.shared.design.component.AppFloatingHeader
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.core.source.labelResId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.shared.home.AppRecentUpdatesContentList
import org.akkirrai.hibiki.shared.home.AppRecentUpdatesContentState
import org.akkirrai.hibiki.shared.home.appRecentUpdatesAnimeListContent

@Composable
fun RecentUpdatesScreen(
    viewModel: HomeViewModel,
    onBackClick: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val libraryStatusByAnimeId = rememberLibraryStatusByAnimeId()
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val movieLabel = stringResource(R.string.anime_meta_movie)
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
        AppRecentUpdatesContentState(
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
                appRecentUpdatesAnimeListContent(
                    items = state.recentlyUpdated,
                    onAnimeClick = onAnimeClick,
                    libraryStatusByAnimeId = libraryStatusByAnimeId,
                    libraryStatusLabel = { category -> stringResource(category.labelResId) },
                    announcementLabel = announcementLabel,
                    movieLabel = movieLabel,
                    modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding),
                )
                if (state.isRecentUpdatesLoadingMore) {
                    item(key = "recent_updates_loading_more") {
                        AppLoadMoreState(
                            isLoading = true,
                            errorMessage = null,
                            onRetry = viewModel::loadMoreRecentUpdates,
                        )
                    }
                }
            }
        }
        AppFloatingHeader(
            title = stringResource(R.string.home_recent_updates),
            onBackClick = onBackClick,
            backContentDescription = stringResource(R.string.cd_back),
            modifier = Modifier,
            includeStatusBarsPadding = false,
        )
    }
}

private const val RECENT_UPDATES_PREFETCH_DISTANCE = 4
