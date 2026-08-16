package org.akkirrai.hibiki.shared.details.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppDetailsHeroMedia(
    imageContent: @Composable () -> Unit,
    frameContent: (@Composable () -> Unit)?,
    playbackContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        imageContent()
        frameContent?.invoke()
        playbackContent()
    }
}
