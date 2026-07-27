package org.akkirrai.hibiki.shared.design.component

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.text.preventTrailingOrphanWrap

@Composable
fun AppVerticalAnimeListItem(
    anime: Anime,
    metaText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    posterWidth: Dp = 104.dp,
    descriptionMaxLines: Int = 5,
    trailingContent: (@Composable () -> Unit)? = null,
    metaContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    posterContent: @Composable BoxScope.() -> Unit,
    posterFooterContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(posterWidth)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            posterContent()
            posterFooterContent?.let { content ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.48f to Color.Black.copy(alpha = 0.04f),
                                    0.76f to Color.Black.copy(alpha = 0.32f),
                                    1f to Color.Black.copy(alpha = 0.68f),
                                ),
                            ),
                        )
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    content()
                }
            }
        }

        var titleLineCount by remember(anime.id, anime.title) { mutableIntStateOf(1) }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = anime.title.preventTrailingOrphanWrap(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { layout ->
                        if (titleLineCount != layout.lineCount) titleLineCount = layout.lineCount
                    },
                )
                trailingContent?.let {
                    Spacer(modifier = Modifier.width(6.dp))
                    it()
                }
            }

            if (metaContent != null) {
                metaContent()
            } else if (metaText.isNotBlank()) {
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            anime.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = descriptionMaxLines.coerceAtMost(
                        (3 + (3 - titleLineCount).coerceAtLeast(0)).coerceAtLeast(1),
                    ),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            supportingContent?.invoke()
        }
    }
}

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
    if (FORCE_POSTER_CARD_STYLE) {
        appPosterAnimeListContent(
            items = items,
            metaText = metaText,
            onAnimeClick = onAnimeClick,
            modifier = modifier,
            posterContent = posterContent,
            posterFooterContent = posterFooterContent,
            onItemVisible = onItemVisible,
        )
        return
    }

    appLegacyVerticalAnimeListContent(
        items = items,
        metaText = metaText,
        onAnimeClick = onAnimeClick,
        modifier = modifier,
        posterContent = posterContent,
        posterFooterContent = posterFooterContent,
        onItemVisible = onItemVisible,
    )
}

private fun LazyListScope.appLegacyVerticalAnimeListContent(
    items: List<Anime>,
    metaText: @Composable (Anime) -> String,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    posterContent: @Composable BoxScope.(Anime) -> Unit,
    posterFooterContent: (@Composable (Anime) -> Unit)? = null,
    onItemVisible: ((Anime) -> Unit)? = null,
) {
    items(items, key = Anime::id) { anime ->
        LaunchedEffect(anime.id) {
            onItemVisible?.invoke(anime)
        }
        AppVerticalAnimeListItem(
            anime = anime,
            metaText = metaText(anime),
            onClick = { onAnimeClick(anime) },
            modifier = modifier.fillMaxWidth(),
            posterContent = { posterContent(anime) },
            posterFooterContent = posterFooterContent?.let { footer -> { footer(anime) } },
        )
    }
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
        items = items.chunked(2),
        key = { row -> row.first().id },
    ) { row ->
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiDimens.PosterGridItemGap),
            verticalAlignment = Alignment.Top,
        ) {
            row.forEach { anime ->
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
            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
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
            Text(
                text = anime.title.preventTrailingOrphanWrap(),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall,
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

private const val FORCE_POSTER_CARD_STYLE = true
