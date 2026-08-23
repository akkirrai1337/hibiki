package org.akkirrai.hibiki.core.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.design.component.content.AppPosterAnimeCard
import org.akkirrai.hibiki.design.component.poster.AppPosterImage
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.buildCardMeta

@Composable
fun AppSourceSearchAnimeCard(
    anime: Anime,
    announcementLabel: String,
    movieLabel: String,
    onClick: () -> Unit,
    cardWidth: Dp,
) {
    AppPosterAnimeCard(
        anime = anime,
        metaText = anime.buildCardMeta(
            announcementLabel = announcementLabel,
            movieLabel = movieLabel,
        ),
        onClick = onClick,
        modifier = Modifier.width(cardWidth),
        posterContent = {
            AppPosterImage(
                primaryUrl = anime.posterUrl,
                fallbackUrl = anime.posterFallbackUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                placeholder = {
                    AppSourceSearchPosterPlaceholder(modifier = Modifier.fillMaxSize())
                },
            )
        },
    )
}

@Composable
fun AppSourceSearchPosterPlaceholder(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
