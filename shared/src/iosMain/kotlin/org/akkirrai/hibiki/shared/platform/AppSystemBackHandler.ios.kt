package org.akkirrai.hibiki.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
actual fun AppSystemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    DisposableEffect(enabled) {
        IosBackBridge.update(enabled, onBack)
        onDispose { IosBackBridge.update(false, onBack) }
    }
    content()
}
