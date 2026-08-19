package org.akkirrai.hibiki.shared.design.component.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.text.preventTrailingOrphanWrap

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
            val titleStyle = MaterialTheme.typography.titleSmall.copy(
                lineBreak = LineBreak.Paragraph,
                hyphens = Hyphens.None,
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val title = remember(anime.title, maxWidth, titleStyle, density, textMeasurer) {
                    wrapTitleAtWords(
                        text = anime.title.preventTrailingOrphanWrap(),
                        style = titleStyle,
                        maxWidth = with(density) { maxWidth.roundToPx() },
                        textMeasurer = textMeasurer,
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

private fun wrapTitleAtWords(
    text: String,
    style: TextStyle,
    maxWidth: Int,
    textMeasurer: TextMeasurer,
): String {
    if (maxWidth <= 0) return text

    val words = text.trim().split(Regex("\\s+"))
    if (words.size <= 1) return text

    val lines = mutableListOf<String>()
    var currentLine = ""
    for (word in words) {
        val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
        val measurement = textMeasurer.measure(
            text = AnnotatedString(candidate),
            style = style,
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth),
        )
        if (currentLine.isNotEmpty() && measurement.didOverflowWidth) {
            lines += currentLine
            currentLine = word
        } else {
            currentLine = candidate
        }
    }
    if (currentLine.isNotEmpty()) lines += currentLine

    if (lines.size <= 2) return lines.joinToString("\n")

    // More text than fits on two lines: the manual word-wrap above already produces a
    // string that occupies exactly two lines, so the outer Text's maxLines=2/Ellipsis
    // never sees an overflow to ellipsize itself. Ellipsize the second line here instead.
    val firstLine = lines[0]
    var secondLine = lines[1]
    while (secondLine.isNotEmpty()) {
        val candidate = "$secondLine…"
        val measurement = textMeasurer.measure(
            text = AnnotatedString(candidate),
            style = style,
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth),
        )
        if (!measurement.didOverflowWidth) {
            secondLine = candidate
            break
        }
        secondLine = secondLine.dropLast(1).trimEnd()
    }

    return "$firstLine\n$secondLine"
}
