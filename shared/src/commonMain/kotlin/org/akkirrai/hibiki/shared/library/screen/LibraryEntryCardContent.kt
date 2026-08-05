package org.akkirrai.hibiki.shared.library.screen
import org.akkirrai.hibiki.shared.library.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.poster.AppImagePlaceholder
import org.akkirrai.hibiki.shared.design.component.poster.AppPosterImage
import org.akkirrai.hibiki.shared.catalog.model.buildCardMeta

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
