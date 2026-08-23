package org.akkirrai.hibiki.home.screen

import org.akkirrai.hibiki.home.ui.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.design.component.poster.AppImagePlaceholder
import org.akkirrai.hibiki.design.component.poster.AppPosterImage
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.buildCardMeta
import org.akkirrai.hibiki.home.state.HomePersonalEmptyState

@Composable
fun AppHomeFeedList(
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    state: LazyListState = rememberLazyListState(),
    horizontalPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = horizontalPadding,
            top = topContentPadding,
            end = horizontalPadding,
            bottom = bottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

fun LazyListScope.appHomeFeedContent(
    continueAnime: Anime?,
    recentlyWatched: List<Anime>,
    recentlyAddedToLibrary: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
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
    onBrowseCatalog: () -> Unit,
    onOpenLibrary: () -> Unit,
    sourceBadgeContent: @Composable (Anime) -> Unit,
) {
    appHomeContinueWatchingSection(
        anime = continueAnime,
        sectionTitle = continueSectionTitle,
        emptyTitle = continueEmptyTitle,
        emptyMessage = continueEmptyMessage,
        openHint = continueOpenHint,
        sectionIcon = HomeHistoryIcon,
        meta = { anime -> anime.buildCardMeta(announcementLabel, movieLabel) },
        onClick = onAnimeClick,
        imageContent = { currentAnime ->
            AppHomePoster(modifier = Modifier.fillMaxSize()) {
                AppPosterImage(
                    primaryUrl = currentAnime.posterUrl,
                    fallbackUrl = currentAnime.posterFallbackUrl,
                    contentDescription = currentAnime.title,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { AppImagePlaceholder() },
                )
            }
        },
        trailingContent = { anime -> sourceBadgeContent(anime) },
    )
    appHomeAnimeSection(
        title = recentlyWatchedTitle,
        items = recentlyWatched,
        onAnimeClick = onAnimeClick,
        icon = HomeHistoryIcon,
        metaText = { anime -> anime.buildCardMeta(announcementLabel, movieLabel) },
        posterContent = { anime ->
            AppPosterImage(
                primaryUrl = anime.posterUrl,
                fallbackUrl = anime.posterFallbackUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                placeholder = { AppHomePosterPlaceholder() },
            )
        },
    )
    appHomeAnimeSection(
        title = recentlyAddedTitle,
        items = recentlyAddedToLibrary,
        onAnimeClick = onAnimeClick,
        icon = HomeRecentlyAddedIcon,
        metaText = { anime -> anime.buildCardMeta(announcementLabel, movieLabel) },
        posterContent = { anime ->
            AppPosterImage(
                primaryUrl = anime.posterUrl,
                fallbackUrl = anime.posterFallbackUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                placeholder = { AppHomePosterPlaceholder() },
            )
        },
        onHeaderClick = onOpenLibrary,
    )
    appHomePersonalEmptySection(
        visible = continueAnime == null && recentlyWatched.isEmpty() && recentlyAddedToLibrary.isEmpty(),
        title = personalEmptyTitle,
        message = personalEmptyMessage,
        actionLabel = personalEmptyActionLabel,
        onActionClick = onBrowseCatalog,
    )
}

fun LazyListScope.appHomePersonalEmptySection(
    visible: Boolean,
    title: String,
    message: String,
    actionLabel: String,
    onActionClick: () -> Unit,
) {
    if (!visible) return

    item {
        HomePersonalEmptyState(
            title = title,
            message = message,
            actionLabel = actionLabel,
            onActionClick = onActionClick,
        )
    }
}
