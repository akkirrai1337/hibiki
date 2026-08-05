package org.akkirrai.hibiki.app.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import org.akkirrai.hibiki.shared.layout.AppLayoutEnvironment
import org.akkirrai.hibiki.shared.layout.AppNavigationBarMode
import org.akkirrai.hibiki.shared.layout.AppScreenEdgePolicy

@Composable
internal fun androidSharedAppLayoutEnvironment(density: Density): AppLayoutEnvironment =
    AppLayoutEnvironment(
        isProvided = true,
        topSystemInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() },
        bottomSystemInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() },
        navigationBarMode = AppNavigationBarMode.Inset,
        edgePolicy = AppScreenEdgePolicy.ContentSafe,
    )
