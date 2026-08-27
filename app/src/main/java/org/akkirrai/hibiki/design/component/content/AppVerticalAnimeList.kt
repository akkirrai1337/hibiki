package org.akkirrai.hibiki.design.component.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.text.preventTrailingOrphanWrap

fun LazyListScope.appVerticalAnimeListContent(
    items: List<Anime>,
    metaText: @Composable (Anime) -> String,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    posterContent: @Composable BoxScope.(Anime) -> Unit,
    posterFooterContent: (@Composable (Anime) -> Unit)? = null,
    onItemVisible: ((Anime) -> Unit)? = null,
) {
    appPosterAnimeListContent(
        items = items,
        metaText = metaText,
        onAnimeClick = onAnimeClick,
        modifier = modifier,
        posterContent = posterContent,
        posterFooterContent = posterFooterContent,
        onItemVisible = onItemVisible,
    )
}

fun LazyListScope.appPosterAnimeListContent(
    items: List<Anime>,
    metaText: @Composable (Anime) -> String,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    posterContent: @Composable BoxScope.(Anime) -> Unit,
    posterFooterContent: (@Composable (Anime) -> Unit)? = null,
    onItemVisible: ((Anime) -> Unit)? = null,
) {
    items(
        count = (items.size + 1) / 2,
        key = { rowIndex -> items[rowIndex * 2].id },
        contentType = { "anime_poster_row" },
    ) { rowIndex ->
        val firstItemIndex = rowIndex * 2
        val lastItemIndex = minOf(firstItemIndex + 2, items.size)
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiDimens.PosterGridItemGap),
            verticalAlignment = Alignment.Top,
        ) {
            for (itemIndex in firstItemIndex until lastItemIndex) {
                val anime = items[itemIndex]
                LaunchedEffect(anime.id) {
                    onItemVisible?.invoke(anime)
                }
                AppPosterAnimeCard(
                    anime = anime,
                    metaText = metaText(anime),
                    onClick = { onAnimeClick(anime) },
                    modifier = Modifier.weight(1f),
                    posterContent = { posterContent(anime) },
                    posterFooterContent = posterFooterContent?.let { footer ->
                        { footer(anime) }
                    },
                )
            }
            if (lastItemIndex - firstItemIndex == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun AppPosterAnimeCard(
    anime: Anime,
    metaText: String,
    onClick: () -> Unit,
    posterContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    metaContent: (@Composable () -> Unit)? = null,
    posterFooterContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(UiDimens.PosterCardCorner))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            posterContent()
            posterFooterContent?.let { content ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(UiDimens.PosterFooterHeight)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.48f to Color.Black.copy(alpha = 0.08f),
                                    1f to Color.Black.copy(alpha = 0.68f),
                                ),
                            ),
                        )
                        .padding(
                            horizontal = UiDimens.PosterFooterHorizontalPadding,
                            vertical = UiDimens.PosterFooterVerticalPadding,
                        ),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    content()
                }
            }
        }

        Column(
            modifier = Modifier.padding(
                horizontal = UiDimens.PosterCardContentHorizontalPadding,
                vertical = UiDimens.PosterCardContentVerticalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(UiDimens.PosterCardContentGap),
        ) {
            val titleStyle = MaterialTheme.typography.titleSmall.copy(
                lineBreak = LineBreak.Paragraph,
                hyphens = Hyphens.None,
            )
            Text(
                text = anime.title.preventTrailingOrphanWrap(),
                modifier = Modifier.fillMaxWidth(),
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (metaContent != null) {
                metaContent()
            } else if (metaText.isNotBlank()) {
                Text(
                    text = metaText,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
