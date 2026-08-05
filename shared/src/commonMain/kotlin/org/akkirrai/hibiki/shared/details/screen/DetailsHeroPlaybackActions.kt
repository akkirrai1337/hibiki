package org.akkirrai.hibiki.shared.details.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AppDetailsHeroPlaybackActions(
    resumeTitle: String?,
    resumeSubtitle: String?,
    onResumeClick: (() -> Unit)?,
    trailerEnabled: Boolean,
    onTrailerClick: () -> Unit,
    trailerContentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
        resumeTitle != null && resumeSubtitle != null && onResumeClick != null -> {
            Surface(
                onClick = onResumeClick,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.58f),
                border = BorderStroke(DetailsPlaybackResumeBorderWidth, Color.White.copy(alpha = 0.28f)),
                contentColor = Color.White,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = DetailsPlaybackResumeHorizontalPadding, vertical = DetailsPlaybackResumeVerticalPadding),
                    horizontalArrangement = Arrangement.spacedBy(DetailsPlaybackResumeContentGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(DetailsPlaybackResumeIconSize),
                    )
                    Column {
                        Text(
                            text = resumeTitle,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = resumeSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                }
            }
        }

            trailerEnabled -> {
            Surface(
                onClick = onTrailerClick,
                modifier = Modifier.size(DetailsPlaybackTrailerButtonSize),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.38f),
                border = BorderStroke(DetailsPlaybackTrailerBorderWidth, Color.White.copy(alpha = 0.32f)),
                contentColor = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = trailerContentDescription,
                        modifier = Modifier.size(DetailsPlaybackTrailerIconSize),
                    )
                }
            }
        }
    }
    }
}
