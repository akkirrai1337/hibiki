package org.akkirrai.hibiki.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun EpisodesListHeader(
    sectionTitle: String,
    watchedSummary: String,
    resumeTitle: String?,
    resumePosition: String?,
    resumeProgressFraction: Float?,
    onResumeClick: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EpisodesHeaderSectionGap)) {
        if (resumeTitle != null && resumePosition != null && resumeProgressFraction != null && onResumeClick != null) {
            EpisodeResumeCard(
                title = resumeTitle,
                position = resumePosition,
                progressFraction = resumeProgressFraction,
                onClick = onResumeClick,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EpisodesHeaderTitleHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = watchedSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EpisodeResumeCard(
    title: String,
    position: String,
    progressFraction: Float,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(EpisodeResumeCardCornerRadius)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box {
            Row(
                modifier = Modifier.padding(EpisodeResumeCardPadding),
                horizontalArrangement = Arrangement.spacedBy(EpisodeResumeCardContentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(EpisodeResumePlayTileSize)
                        .clip(RoundedCornerShape(EpisodeResumePlayTileCornerRadius))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = position,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(EpisodeResumeChevronSize),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
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
