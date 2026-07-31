package org.akkirrai.hibiki.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun AppSystemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val currentOnBack by rememberUpdatedState(onBack)
    val token = remember { IosBackBridge.register() }
    DisposableEffect(token, enabled) {
        IosBackBridge.update(token, enabled) { currentOnBack() }
        onDispose { IosBackBridge.unregister(token) }
    }
    content()
}
