package org.akkirrai.hibiki.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.app.AppProductionRoot
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination
import org.akkirrai.hibiki.shared.navigation.reduce

/**
 * Desktop entry point for the production shared shell.
 */
fun main() = application {
    val catalogRepository = DesktopCatalogRepository()
    val homeRepository = DesktopHomeRepository(catalogRepository)
    Window(
        onCloseRequest = {
            catalogRepository.close()
            exitApplication()
        },
        title = "hibiki",
        state = rememberWindowState(width = 1180.dp, height = 760.dp),
    ) {
            MaterialTheme(colorScheme = HibikiLightColorScheme, typography = HibikiTypography) {
                Surface {
                    var navigationState by remember {
                        mutableStateOf(AppNavigationState(AppTopLevelDestination.CATALOG))
                    }
                    AppProductionRoot(
                        currentDestination = navigationState.currentTopLevel,
                        destinations = listOf(
                            AppTopLevelDestination.HOME,
                            AppTopLevelDestination.CATALOG,
                        ),
                        onNavigationEvent = { event: AppNavigationEvent ->
                            navigationState = navigationState.reduce(event)
                        },
                    ) {
                        when (navigationState.currentTopLevel) {
                            AppTopLevelDestination.HOME -> DesktopHomeScreen(repository = homeRepository)
                            AppTopLevelDestination.CATALOG -> DesktopCatalogScreen(
                                repository = catalogRepository,
                                onAnimeClick = {},
                            )
                            else -> Unit
                        }
                    }
                }
            }
    }
}
