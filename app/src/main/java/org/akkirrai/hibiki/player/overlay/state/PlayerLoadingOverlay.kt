package org.akkirrai.hibiki.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

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
        CircularProgressIndicator(
            modifier = Modifier.size(PlayerCenterPrimaryButtonSize),
            color = Color.White,
            strokeWidth = PlayerLoadingIndicatorStrokeWidth,
        )
    }
}
