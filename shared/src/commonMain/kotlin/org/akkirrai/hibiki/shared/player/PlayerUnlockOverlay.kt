package org.akkirrai.hibiki.shared.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppPlayerUnlockOverlay(
    visible: Boolean,
    label: String,
    onClick: () -> Unit,
    iconContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(160)),
        exit = fadeOut(animationSpec = tween(120)),
    ) {
        AppPlayerUnlockButton(
            label = label,
            onClick = onClick,
            iconContent = { iconContent(Modifier.size(PlayerUnlockOverlayIconSize)) },
        )
    }
}
