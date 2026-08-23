package org.akkirrai.hibiki.player

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.akkirrai.hibiki.app.navigation.AppPlayerSettingsDestination as PlayerSettingsDestination

@Composable
fun AppPlayerSettingsSheet(
    destination: PlayerSettingsDestination,
    title: @Composable (PlayerSettingsDestination) -> String,
    onBack: () -> Unit,
    backContent: @Composable () -> Unit,
    content: LazyListScope.(PlayerSettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = PlayerSettingsSheetBottomPadding),
    ) {
        // Mirrors `destination` one-way (same pattern as AppProductionRoot's tab NavHost) --
        // the reducer keeps sole authority over which page is open and how Back steps between
        // pages (see PlaybackHostContent's BackHandler); this NavHost only renders whichever
        // page `destination` currently says is active. Its own backstack is pinned to exactly
        // one entry at all times (inclusive = true) so it never registers system-back
        // interception of its own, same fix as the tab NavHost's back-gesture bug.
        val navController = rememberNavController()
        LaunchedEffect(destination) {
            navController.navigate(destination.name) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
        NavHost(
            navController = navController,
            startDestination = PlayerSettingsDestination.Root.name,
            enterTransition = {
                val direction = playerSettingsTransitionDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(animationSpec = tween(180)) { width -> direction * width / 5 } +
                    fadeIn(animationSpec = tween(140))
            },
            exitTransition = {
                val direction = playerSettingsTransitionDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(animationSpec = tween(180)) { width -> -direction * width / 5 } +
                    fadeOut(animationSpec = tween(120))
            },
            popEnterTransition = {
                val direction = playerSettingsTransitionDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(animationSpec = tween(180)) { width -> direction * width / 5 } +
                    fadeIn(animationSpec = tween(140))
            },
            popExitTransition = {
                val direction = playerSettingsTransitionDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(animationSpec = tween(180)) { width -> -direction * width / 5 } +
                    fadeOut(animationSpec = tween(120))
            },
        ) {
            PlayerSettingsDestination.entries.forEach { pageDestination ->
                composable(pageDestination.name) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (pageDestination != PlayerSettingsDestination.Root) {
                            PlayerSettingsHeader(
                                title = title(pageDestination),
                                showBack = true,
                                onBack = onBack,
                                backContent = backContent,
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = PlayerSettingsSheetMaxHeight),
                            contentPadding = PaddingValues(bottom = PlayerSettingsSheetListBottomPadding),
                            verticalArrangement = Arrangement.spacedBy(PlayerSettingsSheetItemGap),
                            userScrollEnabled = true,
                        ) {
                            content(pageDestination)
                        }
                    }
                }
            }
        }
    }
}

private fun playerSettingsTransitionDirection(initialRoute: String?, targetRoute: String?): Int {
    val initialOrdinal = initialRoute?.let { PlayerSettingsDestination.valueOf(it).ordinal } ?: 0
    val targetOrdinal = targetRoute?.let { PlayerSettingsDestination.valueOf(it).ordinal } ?: 0
    return if (targetOrdinal > initialOrdinal) 1 else -1
}

/** Shared settings-panel shell; platform code supplies settings content/actions. */
@Composable
fun AppPlayerSettingsLayer(
    onDismissRequest: () -> Unit,
    nowMs: () -> Long,
    backHandler: @Composable (Boolean, () -> Unit) -> Unit,
    content: @Composable ((() -> Unit)) -> Unit,
) {
    // Keep the settings sheet above terminal playback states, including the
    // error surface emitted by AppPlaybackOverlayHost.
    Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
        AppPlayerOverlayPanel(
            onDismissRequest = onDismissRequest,
            widthFraction = PlayerSettingsPanelWidthFraction,
            maxWidth = PlayerSettingsPanelMaxWidth,
            restingOffsetY = PlayerSettingsPanelRestingOffsetY,
            swipeToDismissEnabled = false,
            nowMs = nowMs,
            backHandler = backHandler,
            content = content,
        )
    }
}
