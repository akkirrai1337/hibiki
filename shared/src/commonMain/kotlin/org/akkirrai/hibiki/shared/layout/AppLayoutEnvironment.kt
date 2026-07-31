package org.akkirrai.hibiki.shared.layout

import androidx.compose.runtime.staticCompositionLocalOf
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
