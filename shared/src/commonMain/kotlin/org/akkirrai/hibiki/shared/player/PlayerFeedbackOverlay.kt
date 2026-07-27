package org.akkirrai.hibiki.shared.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppPlayerFeedbackOverlay(
    visible: Boolean,
    label: String,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(140)) + scaleIn(initialScale = 0.92f, animationSpec = tween(140)),
        exit = fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(160)),
    ) {
        Surface(
            shape = RoundedCornerShape(PlayerFeedbackOverlayCornerRadius),
            color = Color.Black.copy(alpha = 0.62f),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(
                    horizontal = horizontalPadding,
                    vertical = PlayerFeedbackOverlayVerticalPadding,
                ),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
