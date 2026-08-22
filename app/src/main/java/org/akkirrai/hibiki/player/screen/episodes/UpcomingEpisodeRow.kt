package org.akkirrai.hibiki.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * Read-only row for the episode the source hasn't published yet. Unlike a real episode row, this
 * one doesn't stretch to the full list width -- it wraps its own content (headline + chip) so it
 * reads as a small trailing note rather than one more full-width card, and left-aligns within the
 * list. Not clickable: there's nothing to play yet.
 */
@Composable
fun UpcomingEpisodeRow(
    headline: String,
    countdownText: String,
    modifier: Modifier = Modifier,
) {
    // A full pill, not the squarer shape real episode cards use -- this row isn't part of that
    // full-width group anymore, so it reads better as its own small, clearly-different shape.
    val shape = RoundedCornerShape(50)
    Surface(
        modifier = modifier.clip(shape),
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
            )
            UpcomingEpisodeCountdownChip(countdownText)
        }
    }
}

// A smaller clone of DetailsNextEpisodeChip (used on the details hero) -- that one's sized for a
// large poster header, and read as too wide/heavy squeezed into a compact list row here.
@Composable
private fun UpcomingEpisodeCountdownChip(text: String) {
    val chipColor = Color(0xFF80DF87)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(chipColor.copy(alpha = 0.2f))
            .padding(horizontal = UpcomingEpisodeChipHorizontalPadding, vertical = UpcomingEpisodeChipVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(UpcomingEpisodeChipContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.HourglassEmpty, null, Modifier.size(UpcomingEpisodeChipIconSize), tint = chipColor)
        Text(text, color = chipColor, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
