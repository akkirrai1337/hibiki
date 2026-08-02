package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.text.preventTrailingOrphanWrap

@Composable
fun AppPlayerTopOverlay(
    title: String,
    subtitle: String,
    playlistEnabled: Boolean,
    backContent: @Composable () -> Unit,
    playlistContent: @Composable () -> Unit,
    topContentInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.82f),
                    1f to Color.Transparent,
                ),
            )
            .padding(
                start = PlayerTopOverlayHorizontalPadding,
                top = PlayerTopOverlayVerticalPadding + topContentInset,
                end = PlayerTopOverlayHorizontalPadding,
                bottom = PlayerTopOverlayVerticalPadding,
            ),
    ) {
        Box(
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            backContent()
        }

        if (playlistEnabled) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                playlistContent()
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    horizontal = PlayerTopOverlayTitleHorizontalPadding,
                    vertical = PlayerTopOverlayTitleVerticalPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PlayerTopOverlayTitleGap),
        ) {
            Text(
                text = title.preventTrailingOrphanWrap(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
