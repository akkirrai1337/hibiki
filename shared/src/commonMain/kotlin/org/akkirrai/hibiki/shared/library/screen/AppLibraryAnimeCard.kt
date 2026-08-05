package org.akkirrai.hibiki.shared.library.screen
import org.akkirrai.hibiki.shared.library.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.content.AppPosterAnimeCard
import org.akkirrai.hibiki.shared.catalog.model.Anime

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
