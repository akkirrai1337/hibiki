package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AppPlayerErrorOverlay(
    message: String,
    title: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = PlayerErrorOverlayHorizontalPadding)
                .widthIn(max = PlayerErrorOverlayMaxWidth),
            shape = RoundedCornerShape(PlayerErrorOverlayCornerRadius),
            color = Color.Black.copy(alpha = 0.84f),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = PlayerErrorOverlayContentHorizontalPadding,
                    vertical = PlayerErrorOverlayContentVerticalPadding,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PlayerErrorOverlayContentGap),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = retryLabel,
                    modifier = Modifier
                        .alpha(0.92f)
                        .clickable(onClick = onRetry),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
