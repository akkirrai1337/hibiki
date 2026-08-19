package org.akkirrai.hibiki.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun PlaylistEpisodeRow(
    headline: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val titleColor = if (selected) Color.White else Color.White.copy(alpha = 0.92f)
    val subtitleColor = if (selected) Color.White else Color.White.copy(alpha = 0.72f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(PlaylistEpisodeRowCornerRadius)),
        color = if (selected) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = PlaylistEpisodeRowHorizontalPadding,
                vertical = PlaylistEpisodeRowVerticalPadding,
            ),
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = PlaylistEpisodeRowSubtitleTopPadding),
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
