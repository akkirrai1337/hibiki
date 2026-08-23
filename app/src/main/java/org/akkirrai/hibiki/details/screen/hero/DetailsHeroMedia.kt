package org.akkirrai.hibiki.details.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.AnimeTrailer

data class DetailsHeroMediaData(
    val trailer: AnimeTrailer?,
)

fun resolveDetailsHeroMediaData(
    anime: Anime,
): DetailsHeroMediaData {
    return DetailsHeroMediaData(
        trailer = anime.trailer?.takeIf { it.playbackUrl != null },
    )
}

@Composable
fun AppDetailsHeroMedia(
    imageContent: @Composable () -> Unit,
    frameContent: (@Composable () -> Unit)?,
    playbackContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        imageContent()
        frameContent?.invoke()
        playbackContent()
    }
}
