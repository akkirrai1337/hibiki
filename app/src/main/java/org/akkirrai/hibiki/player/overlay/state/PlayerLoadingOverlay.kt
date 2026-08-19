package org.akkirrai.hibiki.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.akkirrai.hibiki.design.component.controls.AppFilledIconButton
import org.akkirrai.hibiki.design.component.controls.AppFilledIconButtonStyle

/**
 * Same visual as the center play/pause button's own loading state (AppPlayerCenterControls) --
 * used everywhere else the player has nothing to toggle yet (no playback object at all), so
 * every loading moment in the player reads as one indicator, not several different-looking ones.
 */
@Composable
fun AppPlayerLoadingOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AppFilledIconButton(
            onClick = {},
            modifier = Modifier.size(PlayerCenterPrimaryButtonSize),
            style = AppFilledIconButtonStyle.DarkOverlay,
            content = {
                CircularProgressIndicator(
                    modifier = Modifier.size(PlayerCenterPlayIconSize),
                    color = Color.White,
                    strokeWidth = PlayerLoadingIndicatorStrokeWidth,
                )
            },
        )
    }
}
