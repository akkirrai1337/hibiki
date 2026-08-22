package org.akkirrai.hibiki.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.akkirrai.hibiki.details.screen.DetailsNextEpisodeChip

/**
 * Read-only row for the episode the source hasn't published yet -- same countdown chip as the
 * details screen's hero, just laid out as a trailing list row instead of a floating badge. Not
 * clickable: there's nothing to play yet.
 */
@Composable
fun UpcomingEpisodeRow(
    headline: String,
    countdownText: String,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(shape),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EpisodeRowHorizontalPadding, vertical = EpisodeRowVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(EpisodeRowContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            DetailsNextEpisodeChip(text = countdownText, icon = Icons.Outlined.HourglassEmpty)
        }
    }
}
