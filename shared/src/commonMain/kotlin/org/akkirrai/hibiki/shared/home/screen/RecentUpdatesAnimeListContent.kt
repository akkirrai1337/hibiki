package org.akkirrai.hibiki.shared.home.screen

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.poster.AppPosterImage
import org.akkirrai.hibiki.shared.design.component.poster.AppPosterPlaceholder
import org.akkirrai.hibiki.shared.design.component.content.appVerticalAnimeListContent
import org.akkirrai.hibiki.shared.library.screen.LibraryStatusPosterFooter
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.screen.icon
import org.akkirrai.hibiki.shared.catalog.model.Anime

fun LazyListScope.appHomeAnimeListContent(
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    libraryStatusLabel: @Composable (LibraryCategory) -> String,
    metaText: @Composable (Anime) -> String,
    modifier: Modifier = Modifier,
) {
    appVerticalAnimeListContent(
        items = items,
        metaText = metaText,
        onAnimeClick = onAnimeClick,
        modifier = modifier,
        posterContent = { anime ->
            AppPosterImage(
                primaryUrl = anime.posterUrl,
                fallbackUrl = anime.posterFallbackUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    AppPosterPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f),
                    )
                },
            )
        },
        posterFooterContent = { anime ->
            libraryStatusByAnimeId[anime.id]?.let { category ->
                LibraryStatusPosterFooter(
                    label = libraryStatusLabel(category),
                    icon = category.icon(),
                )
            }
        },
    )
}
