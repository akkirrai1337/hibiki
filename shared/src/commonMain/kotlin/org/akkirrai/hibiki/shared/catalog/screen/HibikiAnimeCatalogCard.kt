package org.akkirrai.hibiki.shared.catalog.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import org.akkirrai.hibiki.shared.design.component.content.AppPosterAnimeCard
import org.akkirrai.hibiki.shared.catalog.model.Anime

@Composable
internal fun AnimeCatalogCard(anime: Anime, onClick: () -> Unit) {
    AppPosterAnimeCard(
        anime = anime,
        metaText = listOf(anime.subtitle, "${anime.status} · ${anime.episodesLabel}")
            .filter(String::isNotBlank)
            .joinToString("\n"),
        onClick = onClick,
        posterContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = anime.title.take(1),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    )
}
