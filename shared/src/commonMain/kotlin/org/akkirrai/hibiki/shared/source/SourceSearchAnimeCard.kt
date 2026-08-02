package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.design.component.AppPosterAnimeCard
import org.akkirrai.hibiki.shared.design.component.AppPosterImage
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.buildCardMeta

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
