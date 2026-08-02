package org.akkirrai.hibiki.shared.platform

import androidx.compose.runtime.Composable

@Composable
expect fun AppSystemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
)
