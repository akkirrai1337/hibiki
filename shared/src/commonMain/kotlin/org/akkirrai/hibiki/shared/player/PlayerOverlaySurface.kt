package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource

@Composable
fun AppPlayerOverlaySurface(
    scrimAlpha: Float,
    scrimEnabled: Boolean,
    panelAlpha: Float,
    panelScale: Float,
    panelTranslationY: Float,
    widthFraction: Float,
    maxWidth: Dp,
    restingOffsetY: Dp,
    panelModifier: Modifier,
    showHandle: Boolean,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onScrimClick: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ((() -> Unit)) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(enabled = scrimEnabled, onClick = onScrimClick),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .widthIn(max = maxWidth)
                .navigationBarsPadding()
                .padding(
                    horizontal = PlayerOverlayPanelPadding,
                    vertical = PlayerOverlayPanelPadding,
                )
                .offset(y = restingOffsetY)
                .then(panelModifier)
                .graphicsLayer {
                    alpha = panelAlpha
                    scaleX = panelScale
                    scaleY = panelScale
                    translationY = panelTranslationY
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    // Consume clicks inside the panel so the scrim does not receive them.
                },
            shape = RoundedCornerShape(PlayerOverlayPanelCornerRadius),
            color = Color(0xFF121212),
            contentColor = Color.White,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showHandle) {
                    AppPlayerOverlayHandle(
                        onDragDelta = onDragDelta,
                        onDragEnd = onDragEnd,
                    )
                } else {
                    Spacer(modifier = Modifier.size(PlayerOverlayHiddenHandleSpacerSize))
                }
                content(onDismiss)
            }
        }
    }
}
