package org.akkirrai.hibiki.shared.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.model.Anime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHomeFeedZone(
    state: HomeUiState,
    listState: LazyListState,
    pullToRefreshState: PullToRefreshState,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    indicatorTopPadding: Dp,
    continueSectionTitle: String,
    continueEmptyTitle: String,
    continueEmptyMessage: String,
    continueOpenHint: String,
    recentlyWatchedTitle: String,
    recentlyAddedTitle: String,
    announcementLabel: String,
    movieLabel: String,
    personalEmptyTitle: String,
    personalEmptyMessage: String,
    personalEmptyActionLabel: String,
    onRefresh: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onBrowseCatalog: () -> Unit,
    onOpenLibrary: () -> Unit,
    sourceBadgeContent: @Composable (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppHomePullToRefresh(
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicatorTopPadding = indicatorTopPadding,
        modifier = modifier,
    ) {
        AppHomeFeedList(
            state = listState,
            topContentPadding = topContentPadding,
            bottomContentPadding = bottomContentPadding,
        ) {
            appHomeFeedContent(
                continueAnime = state.continueAnime,
                recentlyWatched = state.recentlyWatched,
                recentlyAddedToLibrary = state.recentlyAddedToLibrary,
                onAnimeClick = onAnimeClick,
                continueSectionTitle = continueSectionTitle,
                continueEmptyTitle = continueEmptyTitle,
                continueEmptyMessage = continueEmptyMessage,
                continueOpenHint = continueOpenHint,
                recentlyWatchedTitle = recentlyWatchedTitle,
                recentlyAddedTitle = recentlyAddedTitle,
                announcementLabel = announcementLabel,
                movieLabel = movieLabel,
                personalEmptyTitle = personalEmptyTitle,
                personalEmptyMessage = personalEmptyMessage,
                personalEmptyActionLabel = personalEmptyActionLabel,
                onBrowseCatalog = onBrowseCatalog,
                onOpenLibrary = onOpenLibrary,
                sourceBadgeContent = sourceBadgeContent,
            )
        }
    }
}
