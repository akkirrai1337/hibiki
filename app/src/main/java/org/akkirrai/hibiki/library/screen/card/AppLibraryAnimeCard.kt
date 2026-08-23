package org.akkirrai.hibiki.library.screen
import org.akkirrai.hibiki.library.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import org.akkirrai.hibiki.design.component.content.AppPosterAnimeCard
import org.akkirrai.hibiki.design.component.poster.AppImagePlaceholder
import org.akkirrai.hibiki.design.component.poster.AppPosterImage
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.buildCardMeta

@Composable
fun AppLibraryAnimeCard(
    anime: Anime,
    metaText: String,
    onClick: () -> Unit,
    posterContent: @Composable BoxScope.() -> Unit,
    sourceBadgeContent: (@Composable () -> Unit)? = null,
    posterFooterContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPosterAnimeCard(
        anime = anime,
        metaText = metaText,
        onClick = onClick,
        modifier = modifier,
        posterContent = {
            posterContent()
            sourceBadgeContent?.let { content ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(LibraryAnimeCardSourceBadgePadding),
                ) {
                    content()
                }
            }
        },
        posterFooterContent = posterFooterContent,
    )
}

@Composable
fun AppLibraryEntryCard(
    entry: LibraryEntry,
    announcementLabel: String,
    movieLabel: String,
    onClick: () -> Unit,
    libraryStatusLabel: @Composable (LibraryCategory) -> String,
    sourceBadgeContent: (@Composable (String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val anime = entry.anime
    AppLibraryAnimeCard(
        anime = anime,
        metaText = anime.buildCardMeta(
            announcementLabel = announcementLabel,
            movieLabel = movieLabel,
        ),
        onClick = onClick,
        modifier = modifier,
        posterContent = {
            AppPosterImage(
                primaryUrl = anime.posterUrl,
                fallbackUrl = anime.posterFallbackUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                placeholder = { AppImagePlaceholder() },
            )
        },
        sourceBadgeContent = sourceBadgeContent?.let { badge ->
            { badge(anime.id) }
        },
        posterFooterContent = {
            LibraryStatusPosterFooter(
                label = libraryStatusLabel(entry.category),
                icon = entry.category.icon(),
            )
        },
    )
}

@Composable
fun LibraryStatusPosterFooter(label: String, icon: ImageVector) {
    val isLongLabel = label.length > 8
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isLongLabel) Arrangement.Center else Arrangement.spacedBy(LibraryStatusFooterContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isLongLabel) Icon(icon, null, Modifier.size(LibraryStatusFooterIconSize), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = if (isLongLabel) 12.sp else MaterialTheme.typography.bodySmall.fontSize), color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}
