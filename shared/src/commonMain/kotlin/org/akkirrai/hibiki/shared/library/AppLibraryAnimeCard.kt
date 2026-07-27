package org.akkirrai.hibiki.shared.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.component.AppPosterAnimeCard
import org.akkirrai.hibiki.shared.model.Anime

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
                        .padding(8.dp),
                ) {
                    content()
                }
            }
        },
        posterFooterContent = posterFooterContent,
    )
}
