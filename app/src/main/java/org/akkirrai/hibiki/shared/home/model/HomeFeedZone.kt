package org.akkirrai.hibiki.shared.home

import org.akkirrai.hibiki.shared.home.screen.*
import org.akkirrai.hibiki.shared.home.state.HomeUiState

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.catalog.model.Anime

@Composable
fun AppHomeFeedZone(
    state: HomeUiState,
    listState: LazyListState,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
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
    onAnimeClick: (Anime) -> Unit,
    onBrowseCatalog: () -> Unit,
    onOpenLibrary: () -> Unit,
    sourceBadgeContent: @Composable (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppHomeFeedList(
        state = listState,
        topContentPadding = topContentPadding,
        bottomContentPadding = bottomContentPadding,
        modifier = modifier,
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
