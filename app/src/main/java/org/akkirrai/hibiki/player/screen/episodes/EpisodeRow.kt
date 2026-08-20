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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun EpisodeRow(
    headline: AnnotatedString,
    subtitle: String?,
    inProgress: Boolean,
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
    androidx.compose.material3.Surface(
        // clip must precede clickable -- Surface clips its own background/content to `shape`,
        // but a caller-supplied .clickable() on this outer modifier draws its ripple against the
        // full rectangular layout bounds unless it's clipped first, so the press highlight bled
        // past the row's rounded corners (visible on the first/last row of a grouped list).
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EpisodeRowHorizontalPadding, vertical = EpisodeRowVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(EpisodeRowContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                // expandIn/shrinkOut (not -Horizontally) animate both axes together: the download
                // action is taller than the row's text content, so animating width alone reported
                // this element's full height to the parent Row from frame one, snapping the whole
                // card's height instantly while only its width visibly slid in.
                enter = fadeIn(fadeAnimationSpec) + expandIn(sizeAnimationSpec, expandFrom = Alignment.Center),
                exit = fadeOut(fadeAnimationSpec) + shrinkOut(sizeAnimationSpec, shrinkTowards = Alignment.Center),
            ) {
                downloadAction?.invoke()
            }
        }
    }
}
