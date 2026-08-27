package org.akkirrai.hibiki.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.player.model.EpisodeProgressStatus

@Composable
fun EpisodeRow(
    headline: AnnotatedString,
    subtitle: String?,
    inProgress: Boolean,
    episodeNumber: String,
    status: EpisodeProgressStatus,
    progressFraction: Float?,
    enabled: Boolean,
    showDownloadAction: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(EpisodeRowDefaultCornerRadius),
    onClick: () -> Unit,
    downloadAction: @Composable (() -> Unit)? = null,
) {
    // Each toggled piece (subtitle line, download action) animates its own fade + size instead of
    // relying on animateContentSize() for the whole row: that animates the container's bounds
    // while its children pop in/out instantly, so the label visibly jumps mid-animation and the
    // two animations end up fighting each other instead of reading as one smooth resize.
    val sizeAnimationSpec = tween<androidx.compose.ui.unit.IntSize>(EpisodeRowSizeAnimationDurationMillis)
    val fadeAnimationSpec = tween<Float>(EpisodeRowSizeAnimationDurationMillis)
    val active = status == EpisodeProgressStatus.InProgress
    val rowColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
    androidx.compose.material3.Surface(
        // clip must precede clickable -- Surface clips its own background/content to `shape`,
        // but a caller-supplied .clickable() on this outer modifier draws its ripple against the
        // full rectangular layout bounds unless it's clipped first, so the press highlight bled
        // past the row's rounded corners (visible on the first/last row of a grouped list).
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        color = rowColor,
        shape = shape,
    ) {
        Box {
            Row(
                modifier = Modifier.padding(horizontal = EpisodeRowHorizontalPadding, vertical = EpisodeRowVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(EpisodeRowContentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EpisodeNumberTile(
                    number = episodeNumber,
                    status = status,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(
                        if (inProgress) EpisodeRowProgressTextGap else EpisodeRowTextGap,
                    ),
                ) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AnimatedVisibility(
                        visible = !subtitle.isNullOrBlank(),
                        enter = fadeIn(fadeAnimationSpec) + expandVertically(sizeAnimationSpec),
                        exit = fadeOut(fadeAnimationSpec) + shrinkVertically(sizeAnimationSpec),
                    ) {
                        Text(
                            text = subtitle.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = showDownloadAction,
                    enter = fadeIn(fadeAnimationSpec) + expandIn(sizeAnimationSpec, expandFrom = Alignment.Center),
                    exit = fadeOut(fadeAnimationSpec) + shrinkOut(sizeAnimationSpec, shrinkTowards = Alignment.Center),
                ) {
                    downloadAction?.invoke()
                }
            }
            if (active && progressFraction != null) {
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(EpisodeProgressBarHeight),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f),
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun EpisodeNumberTile(
    number: String,
    status: EpisodeProgressStatus,
) {
    val active = status == EpisodeProgressStatus.InProgress
    val displayNumber = number.takeIf { '.' in it } ?: number.padStart(2, '0')
    Box(
        modifier = Modifier
            .size(EpisodeNumberTileSize)
            .clip(RoundedCornerShape(EpisodeNumberTileCornerRadius))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayNumber,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status == EpisodeProgressStatus.Watched) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(1.dp)
                    .size(EpisodeWatchedBadgeSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(EpisodeWatchedBadgeIconSize),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
