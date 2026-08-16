package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Common visual frame for the player.
 *
 * Platform code owns the video surface and interaction state for now. Keeping
 * the frame common lets us move the complete overlay layer without changing
 * its size, background, or insets on either platform.
 */
@Composable
fun AppPlayerFrame(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        content = content,
    )
}
