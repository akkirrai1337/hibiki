package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.core.model.Anime

@Composable
fun PosterCard(
    anime: Anime,
    metaText: String = anime.subtitle,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    titleBaseMaxLines: Int = 2,
    titleExtraLongTitleLines: Int = 2,
    titleOverflow: TextOverflow = TextOverflow.Clip,
    reservedTitleLines: Int? = null,
    reserveMetaLine: Boolean = false,
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
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        ),
        verticalArrangement = Arrangement.spacedBy(UiDimens.SmallSpacing)
    ) {
        PosterArtwork(anime = anime)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = reservedTextHeight),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AnimeTitleText(
                text = anime.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 1,
                baseMaxLines = titleBaseMaxLines,
                extraLongTitleLines = titleExtraLongTitleLines,
                overflow = titleOverflow,
            )
            if (metaText.isNotBlank()) {
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Transparent,
                    minLines = 1,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun AnimePosterCardItem(
    anime: Anime,
    metaText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp? = null,
    titleBaseMaxLines: Int = 2,
    titleExtraLongTitleLines: Int = 2,
    titleOverflow: TextOverflow = TextOverflow.Clip,
    reservedTitleLines: Int? = null,
    reserveMetaLine: Boolean = false,
) {
    PosterCard(
        anime = anime,
        metaText = metaText,
        onClick = onClick,
        titleBaseMaxLines = titleBaseMaxLines,
        titleExtraLongTitleLines = titleExtraLongTitleLines,
        titleOverflow = titleOverflow,
        reservedTitleLines = reservedTitleLines,
        reserveMetaLine = reserveMetaLine,
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .then(if (height != null) Modifier.height(height) else Modifier),
    )
}

@Composable
private fun PosterArtwork(
    anime: Anime,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(UiDimens.CardCorner))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        PosterImage(
            primaryUrl = anime.posterUrl,
            fallbackUrl = anime.posterFallbackUrl,
            contentDescription = anime.title,
            modifier = Modifier.fillMaxSize(),
            placeholder = { PosterPlaceholder() }
        )
    }
}

@Composable
private fun PosterPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(UiDimens.ScreenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.poster_placeholder),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun TextStyle.resolvedLineHeight(): TextUnit {
    return if (lineHeight != TextUnit.Unspecified) lineHeight else fontSize * 1.2f
}
