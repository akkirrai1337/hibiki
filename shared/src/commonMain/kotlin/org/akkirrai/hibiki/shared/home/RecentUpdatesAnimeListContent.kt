package org.akkirrai.hibiki.shared.home

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.AppPosterImage
import org.akkirrai.hibiki.shared.design.component.AppPosterPlaceholder
import org.akkirrai.hibiki.shared.design.component.appVerticalAnimeListContent
import org.akkirrai.hibiki.shared.library.LibraryStatusPosterFooter
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.icon
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.buildCardMeta

fun LazyListScope.appRecentUpdatesAnimeListContent(
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    libraryStatusLabel: @Composable (LibraryCategory) -> String,
    announcementLabel: String,
    movieLabel: String,
    modifier: Modifier = Modifier,
) {
    appVerticalAnimeListContent(
        items = items,
        metaText = { anime ->
            anime.buildCardMeta(
                announcementLabel = announcementLabel,
                movieLabel = movieLabel,
            )
        },
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
