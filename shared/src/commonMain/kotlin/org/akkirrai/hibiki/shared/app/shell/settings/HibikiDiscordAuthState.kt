package org.akkirrai.hibiki.shared.app.shell.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.akkirrai.hibiki.shared.navigation.AppOverlay
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.activeOverlay
import org.akkirrai.hibiki.shared.navigation.reduceOverlayVisibilityChange

internal class HibikiDiscordAuthState {
    val overlay = AppOverlay.Dialog("discord-auth")
    var pendingToken by mutableStateOf<String?>(null)

    fun isOpen(navigationState: AppNavigationState): Boolean =
        navigationState.activeOverlay == overlay

    fun open(
        navigationState: AppNavigationState,
        setNavigationState: (AppNavigationState) -> Unit,
    ) {
        if (!isOpen(navigationState)) {
            setNavigationState(
                navigationState.reduceOverlayVisibilityChange(
                    overlay = overlay,
                    visible = true,
                ),
            )
        }
    }

    fun close(
        navigationState: AppNavigationState,
        setNavigationState: (AppNavigationState) -> Unit,
    ) {
        if (isOpen(navigationState)) {
            setNavigationState(
                navigationState.reduceOverlayVisibilityChange(
                    overlay = overlay,
                    visible = false,
                ),
            )
        }
    }
}

@Composable
internal fun rememberHibikiDiscordAuthState(): HibikiDiscordAuthState = remember {
    HibikiDiscordAuthState()
}
