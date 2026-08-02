package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
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
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showDownloadAction) downloadAction?.invoke()
        }
    }
}
