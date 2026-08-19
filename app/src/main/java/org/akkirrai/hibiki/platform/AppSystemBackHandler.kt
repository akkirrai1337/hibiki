package org.akkirrai.hibiki.platform

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
fun AppSystemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
    content()
}
