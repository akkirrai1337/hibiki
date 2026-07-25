package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.model.Anime

@Composable
fun AppPosterCard(
    anime: Anime,
    metaText: String = anime.subtitle,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    titleBaseMaxLines: Int = 2,
    titleExtraLongTitleLines: Int = 2,
    titleOverflow: TextOverflow = TextOverflow.Clip,
    reservedTitleLines: Int? = null,
    reserveMetaLine: Boolean = false,
    imageContent: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.bodySmall
    val metaStyle = MaterialTheme.typography.bodySmall
    val reservedTextHeight = remember(density, titleStyle, metaStyle, reservedTitleLines, reserveMetaLine) {
        with(density) {
            val titleHeight = reservedTitleLines
                ?.takeIf { it > 0 }
                ?.let { titleStyle.resolvedLineHeight() * it }
                ?.toDp()
                ?: 0.dp
            val metaHeight = if (reserveMetaLine) metaStyle.resolvedLineHeight().toDp() else 0.dp
            val spacingHeight = if (reserveMetaLine) 4.dp else 0.dp
            titleHeight + metaHeight + spacingHeight
        }
    }

    Column(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
        verticalArrangement = Arrangement.spacedBy(UiDimens.SmallSpacing),
    ) {
        imageContent()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = reservedTextHeight),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = anime.title,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 1,
                maxLines = (titleBaseMaxLines + titleExtraLongTitleLines).coerceAtLeast(1),
                overflow = titleOverflow,
            )
            if (metaText.isNotBlank()) {
                Text(
                    text = metaText,
                    style = metaStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = " ",
                    style = metaStyle,
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    minLines = 1,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun TextStyle.resolvedLineHeight(): TextUnit =
    if (lineHeight != TextUnit.Unspecified) lineHeight else fontSize * 1.2f
