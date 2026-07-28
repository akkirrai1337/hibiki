package org.akkirrai.hibiki.shared.home

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.AppImagePlaceholder
import org.akkirrai.hibiki.shared.design.component.AppPosterImage
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.buildCardMeta

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
