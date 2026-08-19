package org.akkirrai.hibiki.layout

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppNavigationBarMode {
    Inset,
    Overlay,
}

enum class AppScreenEdgePolicy {
    ContentSafe,
    EdgeToEdge,
}

/** Platform-neutral window geometry supplied by each host. */
data class AppLayoutEnvironment(
    val isProvided: Boolean = false,
    val topSystemInset: Dp = 0.dp,
    val bottomSystemInset: Dp = 0.dp,
    val navigationBarMode: AppNavigationBarMode = AppNavigationBarMode.Inset,
    val edgePolicy: AppScreenEdgePolicy = AppScreenEdgePolicy.ContentSafe,
)

val LocalAppLayoutEnvironment = staticCompositionLocalOf { AppLayoutEnvironment() }

@Composable
fun appBottomSystemInsetValue(enabled: Boolean = true): Dp {
    if (!enabled) return 0.dp
    val environment = LocalAppLayoutEnvironment.current
    if (!environment.isProvided) {
        return WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }
    return when (environment.navigationBarMode) {
        AppNavigationBarMode.Inset -> environment.bottomSystemInset
        AppNavigationBarMode.Overlay -> 0.dp
    }
}

@Composable
fun Modifier.appBottomSystemInsetPadding(): Modifier {
    val environment = LocalAppLayoutEnvironment.current
    if (!environment.isProvided) return navigationBarsPadding()
    return padding(bottom = appBottomSystemInsetValue())
}

@Composable
fun Modifier.appTopSystemInsetPadding(): Modifier {
    val environment = LocalAppLayoutEnvironment.current
    if (!environment.isProvided) return statusBarsPadding()
    return padding(top = environment.topSystemInset)
}

@Composable
fun Modifier.appRootTopInsetPadding(enabled: Boolean): Modifier =
    if (enabled) appTopSystemInsetPadding() else this
