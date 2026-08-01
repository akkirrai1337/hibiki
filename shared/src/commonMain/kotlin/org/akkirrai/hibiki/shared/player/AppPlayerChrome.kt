package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared player surface/control composition around a platform media surface. */
@Composable
fun AppPlayerChrome(
    surface: @Composable () -> Unit,
    controlsEnabled: Boolean,
    controls: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        surface()
        if (controlsEnabled) {
            controls()
        }
    }
}
