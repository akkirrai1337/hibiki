package org.akkirrai.hibiki.layout

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun appBottomSystemInsetValue(enabled: Boolean = true): Dp {
    if (!enabled) return 0.dp
    return WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
}

@Composable
fun Modifier.appBottomSystemInsetPadding(): Modifier {
    return navigationBarsPadding()
}

@Composable
fun Modifier.appTopSystemInsetPadding(): Modifier {
    return statusBarsPadding()
}
