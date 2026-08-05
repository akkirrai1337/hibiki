package org.akkirrai.hibiki.shared.app.shell.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppOverlay

@Stable
internal class HibikiAppShellNavigationState {
    val route: MutableState<AppNavigationState> = mutableStateOf(AppNavigationState())
    val libraryFilterOverlay: AppOverlay = AppOverlay.Sheet("library-filter")
}

@Composable
internal fun rememberHibikiAppShellNavigationState(): HibikiAppShellNavigationState =
    remember { HibikiAppShellNavigationState() }
