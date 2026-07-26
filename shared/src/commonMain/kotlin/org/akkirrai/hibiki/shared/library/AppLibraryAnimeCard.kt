package org.akkirrai.hibiki.shared.library

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.AppPosterAnimeCard
import org.akkirrai.hibiki.shared.model.Anime

@Composable
fun AppLibraryAnimeCard(
    anime: Anime,
    metaText: String,
    onClick: () -> Unit,
    posterContent: @Composable BoxScope.() -> Unit,
    posterFooterContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPosterAnimeCard(
        anime = anime,
        metaText = metaText,
        onClick = onClick,
        modifier = modifier,
        posterContent = posterContent,
        posterFooterContent = posterFooterContent,
    )
}
