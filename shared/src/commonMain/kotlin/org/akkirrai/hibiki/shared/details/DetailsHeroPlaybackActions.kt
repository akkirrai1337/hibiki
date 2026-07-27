package org.akkirrai.hibiki.shared.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppDetailsHeroPlaybackActions(
    resumeTitle: String?,
    resumeSubtitle: String?,
    resumeProgress: Float,
    onResumeClick: (() -> Unit)?,
    resumeIconContent: @Composable () -> Unit,
    trailerEnabled: Boolean,
    onTrailerClick: () -> Unit,
    trailerIconContent: @Composable () -> Unit,
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
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                contentColor = Color.White,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    resumeIconContent()
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
            if (resumeProgress > 0f) {
                LinearProgressIndicator(
                    progress = { resumeProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.24f),
                )
            }
        }

            trailerEnabled -> {
            Surface(
                onClick = onTrailerClick,
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.38f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)),
                contentColor = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    trailerIconContent()
                }
            }
        }
    }
    }
}
