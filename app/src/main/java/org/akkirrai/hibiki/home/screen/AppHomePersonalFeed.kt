package org.akkirrai.hibiki.home.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.design.component.content.AppContinueWatchingCard
import org.akkirrai.hibiki.design.component.content.AppPosterAnimeCard
import org.akkirrai.hibiki.design.component.content.SectionHeader
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.buildCardMeta

/**
 * Common Home content for personal sections. The host supplies only strings,
 * icons and platform image/badge slots; the list geometry and card hierarchy
 * are shared with Android and desktop hosts.
 */
fun LazyListScope.appHomePersonalFeedContent(
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
    continueIcon: ImageVector,
    recentlyWatchedIcon: ImageVector,
    recentlyAddedIcon: ImageVector,
    onOpenLibrary: () -> Unit,
    onEmptyContent: @Composable () -> Unit,
    continueImageContent: @Composable BoxScope.(Anime) -> Unit,
    continueTrailingContent: @Composable RowScope.(Anime) -> Unit = {},
    posterContent: @Composable BoxScope.(Anime) -> Unit,
) {
    continueAnime?.let { anime ->
        item {
            Box(modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding)) {
                AppContinueWatchingCard(
                    anime = anime,
                    sectionTitle = continueSectionTitle,
                    emptyTitle = continueEmptyTitle,
                    emptyMessage = continueEmptyMessage,
                    openHint = continueOpenHint,
                    meta = anime.buildCardMeta(announcementLabel, movieLabel),
                    sectionIcon = continueIcon,
                    onClick = { onAnimeClick(anime) },
                    imageContent = { continueImageContent(anime) },
                    trailingContent = { continueTrailingContent(anime) },
                )
            }
        }
    }

    appHomePersonalSection(
        title = recentlyWatchedTitle,
        items = recentlyWatched,
        icon = recentlyWatchedIcon,
        announcementLabel = announcementLabel,
        movieLabel = movieLabel,
        onAnimeClick = onAnimeClick,
        posterContent = posterContent,
    )
    appHomePersonalSection(
        title = recentlyAddedTitle,
        items = recentlyAddedToLibrary,
        icon = recentlyAddedIcon,
        announcementLabel = announcementLabel,
        movieLabel = movieLabel,
        onAnimeClick = onAnimeClick,
        onHeaderClick = onOpenLibrary,
        posterContent = posterContent,
    )

    if (continueAnime == null && recentlyWatched.isEmpty() && recentlyAddedToLibrary.isEmpty()) {
        item { onEmptyContent() }
    }
}

private fun LazyListScope.appHomePersonalSection(
    title: String,
    items: List<Anime>,
    icon: ImageVector,
    announcementLabel: String,
    movieLabel: String,
    onAnimeClick: (Anime) -> Unit,
    posterContent: @Composable BoxScope.(Anime) -> Unit,
    onHeaderClick: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    item {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = UiDimens.SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(UiDimens.SmallSpacing),
        ) {
            SectionHeader(
                title = title,
                actionLabel = onHeaderClick?.let { "\u203A" },
                icon = icon,
                modifier = Modifier
                    .padding(horizontal = UiDimens.ScreenPadding)
                    .clickable(enabled = onHeaderClick != null) { onHeaderClick?.invoke() },
                titleStyle = MaterialTheme.typography.titleLarge,
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cardWidth = (maxWidth - 32.dp - UiDimens.ItemSpacing) / 2
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(UiDimens.ItemSpacing),
                ) {
                    items(items, key = Anime::id) { anime ->
                        AppPosterAnimeCard(
                            anime = anime,
                            metaText = anime.buildCardMeta(announcementLabel, movieLabel),
                            onClick = { onAnimeClick(anime) },
                            modifier = Modifier.width(cardWidth),
                            posterContent = { posterContent(anime) },
                        )
                    }
                }
            }
        }
    }
}
