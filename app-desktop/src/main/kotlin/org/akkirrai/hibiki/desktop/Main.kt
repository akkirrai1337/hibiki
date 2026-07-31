package org.akkirrai.hibiki.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.app.AppProductionRoot
import org.akkirrai.hibiki.shared.details.AppDetailsScreen
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination
import org.akkirrai.hibiki.shared.navigation.currentRoute
import org.akkirrai.hibiki.shared.navigation.reduce
import org.akkirrai.hibiki.shared.navigation.transitionKey
import org.akkirrai.hibiki.shared.layout.AppLayoutEnvironment
import org.akkirrai.hibiki.shared.layout.LocalAppLayoutEnvironment

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
                CompositionLocalProvider(
                    LocalAppLayoutEnvironment provides AppLayoutEnvironment(
                        isProvided = true,
                    ),
                ) {
                    var navigationState by remember {
                        mutableStateOf(AppNavigationState(AppTopLevelDestination.CATALOG))
                    }
                    var selectedAnime by remember { mutableStateOf<Anime?>(null) }
                    Surface(
                        modifier = Modifier.onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.Escape &&
                                navigationState.backStack.isNotEmpty()
                            ) {
                                navigationState = navigationState.reduce(AppNavigationEvent.Back)
                                selectedAnime = null
                                true
                            } else {
                                false
                            }
                        },
                    ) {
                        val detailsRoute = navigationState.currentRoute as? AppRoute.Details
                        AppProductionRoot(
                            currentDestination = navigationState.currentTopLevel,
                            destinations = listOf(
                                AppTopLevelDestination.HOME,
                                AppTopLevelDestination.CATALOG,
                            ),
                            onNavigationEvent = { event: AppNavigationEvent ->
                                navigationState = navigationState.reduce(event)
                            },
                            showBottomBar = navigationState.backStack.isEmpty(),
                            contentTransitionKey = navigationState.currentRoute.transitionKey(),
                        ) {
                            if (detailsRoute != null && selectedAnime != null) {
                                AppDetailsScreen(
                                    anime = requireNotNull(selectedAnime),
                                    onBackClick = {
                                        navigationState = navigationState.reduce(AppNavigationEvent.Back)
                                        selectedAnime = null
                                    },
                                    onRelatedAnimeClick = { anime ->
                                        selectedAnime = anime
                                        navigationState = navigationState.reduce(
                                            AppNavigationEvent.Navigate(AppRoute.Details(anime.id)),
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                when (navigationState.currentTopLevel) {
                                    AppTopLevelDestination.HOME -> DesktopHomeScreen(
                                        repository = homeRepository,
                                        onAnimeClick = { anime ->
                                            selectedAnime = anime
                                            navigationState = navigationState.reduce(
                                                AppNavigationEvent.Navigate(AppRoute.Details(anime.id)),
                                            )
                                        },
                                    )
                                    AppTopLevelDestination.CATALOG -> DesktopCatalogScreen(
                                        repository = catalogRepository,
                                        onAnimeClick = { anime ->
                                            selectedAnime = anime
                                            navigationState = navigationState.reduce(
                                                AppNavigationEvent.Navigate(AppRoute.Details(anime.id)),
                                            )
                                        },
                                    )
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
    }
}
