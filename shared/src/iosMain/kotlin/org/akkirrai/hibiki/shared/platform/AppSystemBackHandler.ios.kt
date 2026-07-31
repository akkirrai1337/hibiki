package org.akkirrai.hibiki.shared.platform

import androidx.compose.runtime.Composable

@Composable
actual fun AppSystemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    content()
}
