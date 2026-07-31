package org.akkirrai.hibiki.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.log.PerfLogger
import org.akkirrai.hibiki.core.discord.DiscordRpcManager
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.feature.profile.SharedAndroidLocalProfileScreen
import org.akkirrai.hibiki.feature.profile.LocalProfileViewModel
import org.akkirrai.hibiki.feature.catalog.SharedAndroidCatalogScreen
import org.akkirrai.hibiki.feature.details.SharedAndroidDetailsScreen
import org.akkirrai.hibiki.feature.home.SharedAndroidHomeScreen
import org.akkirrai.hibiki.feature.home.HomeViewModel
import org.akkirrai.hibiki.feature.library.SharedAndroidLibraryScreen
import org.akkirrai.hibiki.feature.player.EpisodesScreen
import org.akkirrai.hibiki.feature.player.PlayerScreen
import org.akkirrai.hibiki.feature.player.WatchSourcesScreen
import org.akkirrai.hibiki.feature.settings.SharedAndroidSettingsScreen
import org.akkirrai.hibiki.feature.settings.SharedAndroidSourcesScreen
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.shared.design.component.AppBottomBar
import org.akkirrai.hibiki.shared.design.component.AppBottomBarContentExtraPadding
import org.akkirrai.hibiki.shared.design.component.AppBottomBarHeight
import org.akkirrai.hibiki.shared.design.component.AppTopLevelScaffold
import org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.navigation.reduce

@Composable
fun HibikiApp(
    onCheckForUpdates: () -> Unit = {},
    onConfigureNotifications: () -> Unit = {},
) {
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topLevelBottomContentPadding = AppBottomBarHeight + navigationBarBottomPadding + AppBottomBarContentExtraPadding
    val navController = rememberNavController()
    val context = LocalContext.current
    val discordRpcManager = remember(context) { DiscordRpcManager.get(context) }
    val appLanguage = LocalAppLanguage.current
    val destinations = TopLevelDestination.entries
    val currentBackStackEntry = navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry.value?.destination
    val currentRoute = currentDestination?.route
    val isTopLevelDestination = destinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }
    val currentTopLevel = destinations.firstOrNull { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    } ?: TopLevelDestination.Home
    LaunchedEffect(currentRoute, appLanguage) {
        AppLogger.setContext("route", currentRoute ?: "<none>")
        AppLogger.setContext("topLevelRoute", currentTopLevel.route)
        PerfLogger.mark(
            event = "Navigation route changed",
            details = "route=$currentRoute, topLevel=$isTopLevelDestination",
        )
        discordRpcManager.showGeneralStatus(currentRoute)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HibikiNavHost(
            modifier = Modifier.fillMaxSize(),
            navController = navController,
            topLevelBottomContentPadding = topLevelBottomContentPadding,
            isTopLevelDestination = isTopLevelDestination,
            currentTopLevel = currentTopLevel,
            onCheckForUpdates = onCheckForUpdates,
            onConfigureNotifications = onConfigureNotifications,
        )
    }
}

@Composable
private fun HibikiNavHost(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier,
    topLevelBottomContentPadding: Dp = AppBottomBarHeight + AppBottomBarContentExtraPadding,
    isTopLevelDestination: Boolean = false,
    currentTopLevel: TopLevelDestination = TopLevelDestination.Home,
    onCheckForUpdates: () -> Unit = {},
    onConfigureNotifications: () -> Unit = {},
) {
    var sharedNavigationState by remember { mutableStateOf(AppNavigationState()) }
    val baseScreenModifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)

    val topLevelScreenModifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)
        .statusBarsPadding()

    val screenModifier = baseScreenModifier.statusBarsPadding()

    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Home.route,
        enterTransition = { appScreenEnterTransition() },
        exitTransition = { appScreenExitTransition() },
        popEnterTransition = { appScreenPopEnterTransition() },
        popExitTransition = { appScreenPopExitTransition() },
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .clipToBounds()
    ) {
        composable(route = TopLevelDestination.Home.route) { backStackEntry ->
            val context = LocalContext.current
            val homeViewModel: HomeViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = HomeViewModel.Factory(context),
            )
            TopLevelScreenContainer(
                destination = TopLevelDestination.Home,
                destinations = TopLevelDestination.entries,
                onDestinationClick = { destination ->
                    navController.runIfCurrent(backStackEntry) {
                        navController.navigateTopLevelDestination(TopLevelDestination.Home, destination)
                    }
                },
            ) {
                SharedAndroidHomeScreen(
                    viewModel = homeViewModel,
                    onAnimeClick = { anime ->
                        navController.runIfCurrent(backStackEntry) {
                            navController.navigate(AnimeNavType.createDetailsRoute(anime))
                        }
                    },
                    onBrowseCatalog = {
                        navController.runIfCurrent(backStackEntry) {
                            navController.navigateTopLevelDestination(
                                TopLevelDestination.Home,
                                TopLevelDestination.Catalog,
                            )
                        }
                    },
                    onOpenLibrary = {
                        navController.runIfCurrent(backStackEntry) {
                            navController.navigateTopLevelDestination(
                                TopLevelDestination.Home,
                                TopLevelDestination.Library,
                            )
                        }
                    },
                    isActive = isTopLevelDestination && currentTopLevel == TopLevelDestination.Home,
                    bottomContentPadding = topLevelBottomContentPadding,
                    modifier = topLevelScreenModifier,
                )
            }
        }
        topLevelComposable(route = TopLevelDestination.Profile.route) { backStackEntry ->
            TopLevelScreenContainer(
                destination = TopLevelDestination.Profile,
                destinations = TopLevelDestination.entries,
                onDestinationClick = { destination ->
                    navController.runIfCurrent(backStackEntry) {
                        navController.navigateTopLevelDestination(TopLevelDestination.Profile, destination)
                    }
                },
            ) {
                SharedAndroidLocalProfileScreen(
                    onSettingsClick = {
                        navController.runIfCurrent(backStackEntry) {
                            navController.navigate(AnimeNavType.SETTINGS_ROUTE)
                        }
                    },
                    bottomContentPadding = topLevelBottomContentPadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        topLevelComposable(route = TopLevelDestination.Catalog.route) { backStackEntry ->
            TopLevelScreenContainer(
                destination = TopLevelDestination.Catalog,
                destinations = TopLevelDestination.entries,
                onDestinationClick = { destination ->
                    navController.runIfCurrent(backStackEntry) {
                        navController.navigateTopLevelDestination(TopLevelDestination.Catalog, destination)
                    }
                },
            ) {
                SharedAndroidCatalogScreen(
                    onAnimeClick = { anime ->
                        navController.runIfCurrent(backStackEntry) {
                            navController.navigate(AnimeNavType.createDetailsRoute(anime))
                        }
                    },
                    bottomContentPadding = topLevelBottomContentPadding,
                    modifier = topLevelScreenModifier,
                )
            }
        }
        topLevelComposable(route = TopLevelDestination.Library.route) { backStackEntry ->
            TopLevelScreenContainer(
                destination = TopLevelDestination.Library,
                destinations = TopLevelDestination.entries,
                onDestinationClick = { destination ->
                    navController.runIfCurrent(backStackEntry) {
                        navController.navigateTopLevelDestination(TopLevelDestination.Library, destination)
                    }
                },
            ) {
                SharedAndroidLibraryScreen(
                    onAnimeClick = { anime ->
                        navController.runIfCurrent(backStackEntry) {
                            navController.navigate(AnimeNavType.createDetailsRoute(anime))
                        }
                    },
                    isActive = isTopLevelDestination && currentTopLevel == TopLevelDestination.Library,
                    bottomContentPadding = topLevelBottomContentPadding,
                    modifier = topLevelScreenModifier,
                )
            }
        }
        topLevelComposable(route = TopLevelDestination.Sources.route) { backStackEntry ->
            TopLevelScreenContainer(
                destination = TopLevelDestination.Sources,
                destinations = TopLevelDestination.entries,
                onDestinationClick = { destination ->
                    navController.runIfCurrent(backStackEntry) {
                        navController.navigateTopLevelDestination(TopLevelDestination.Sources, destination)
                    }
                },
            ) {
                SharedAndroidSourcesScreen(
                    onAnimeClick = { anime ->
                        navController.runIfCurrent(backStackEntry) {
                            navController.navigate(AnimeNavType.createDetailsRoute(anime))
                        }
                    },
                    bottomContentPadding = topLevelBottomContentPadding,
                    modifier = topLevelScreenModifier,
                )
            }
        }
        composable(
            route = AnimeNavType.SETTINGS_ROUTE,
            enterTransition = { appScreenEnterTransition() },
            exitTransition = { appScreenExitTransition() },
            popEnterTransition = { appScreenPopEnterTransition() },
            popExitTransition = { appScreenPopExitTransition() },
        ) { backStackEntry ->
            DestinationScreenContainer {
                SharedAndroidSettingsScreen(
                    modifier = screenModifier,
                    onCheckForUpdates = {
                        navController.runIfCurrent(backStackEntry, onCheckForUpdates)
                    },
                    onConfigureNotifications = onConfigureNotifications,
                )
            }
        }
        composable(
            route = AnimeNavType.DETAILS_PATTERN,
            arguments = listOf(
                navArgument(AnimeNavType.ID_ARG) { type = NavType.StringType },
                navArgument(AnimeNavType.TITLE_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AnimeNavType.SUBTITLE_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AnimeNavType.EPISODES_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AnimeNavType.STATUS_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AnimeNavType.POSTER_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AnimeNavType.POSTER_FALLBACK_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            ),
            enterTransition = { appScreenEnterTransition() },
            exitTransition = { appScreenExitTransition() },
            popEnterTransition = { appScreenPopEnterTransition() },
            popExitTransition = { appScreenPopExitTransition() }
        ) { backStackEntry ->
            DestinationScreenContainer {
                val anime = animeFromArguments(backStackEntry.arguments)
                LaunchedEffect(backStackEntry.id) {
                    sharedNavigationState = sharedNavigationState.reduce(
                        AppNavigationEvent.Navigate(AppRoute.Details(anime.id)),
                    )
                }
                SharedAndroidDetailsScreen(
                    anime = anime,
                    onBackClick = {
                        navController.runIfCurrent(backStackEntry) {
                            sharedNavigationState = sharedNavigationState.reduce(AppNavigationEvent.Back)
                            navController.navigateUp()
                        }
                    },
                    onRelatedAnimeClick = { anime ->
                        navController.runIfCurrent(backStackEntry) {
                            navController.navigate(AnimeNavType.createDetailsRoute(anime))
                        }
                    },
                    onOpenSources = { anime ->
                        navController.runIfCurrent(backStackEntry) {
                            sharedNavigationState = sharedNavigationState.reduce(
                                AppNavigationEvent.Navigate(AppRoute.WatchSources(anime.id)),
                            )
                            navController.navigateSingleTopTo(AnimeNavType.createWatchSourcesRoute(anime))
                        }
                    },
                    onResumePlayback = { progress ->
                        navController.runIfCurrent(backStackEntry) {
                            sharedNavigationState = sharedNavigationState.reduce(
                                AppNavigationEvent.Navigate(
                                    AppRoute.Player(
                                        sourceId = progress.sourceId,
                                        episodeId = progress.episodeId,
                                        episodeNumber = progress.episodeNumber,
                                    ),
                                ),
                            )
                            navController.navigateSingleTopTo(
                                AnimeNavType.createPlayerRoute(
                                    sourceId = progress.sourceId,
                                    episodeId = progress.episodeId,
                                    episodeNumber = progress.episodeNumber,
                                )
                            )
                        }
                    },
                    modifier = baseScreenModifier,
                )
            }
        }
        composable(
            route = AnimeNavType.WATCH_SOURCES_PATTERN,
            arguments = listOf(
                navArgument(AnimeNavType.ID_ARG) { type = NavType.StringType },
                navArgument(AnimeNavType.TITLE_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AnimeNavType.DOWNLOAD_MODE_ARG) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),
            enterTransition = { appScreenEnterTransition() },
            exitTransition = { appScreenExitTransition() },
            popEnterTransition = { appScreenPopEnterTransition() },
            popExitTransition = { appScreenPopExitTransition() }
        ) { backStackEntry ->
            val context = LocalContext.current
            val dependencies = remember(context) { context.applicationContext.hibikiDependencies() }
            val watchStateRepository = remember(dependencies) { dependencies.watchStateRepository() }
            val routeArgs = backStackEntry.arguments
            val animeId = routeArgs.stringArg(AnimeNavType.ID_ARG)
            val downloadMode = routeArgs.booleanArg(AnimeNavType.DOWNLOAD_MODE_ARG)
            DestinationScreenContainer {
                WatchSourcesScreen(
                    animeId = animeId,
                    onBackClick = {
                        navController.runIfCurrent(backStackEntry) {
                            sharedNavigationState = sharedNavigationState.reduce(AppNavigationEvent.Back)
                            navController.navigateUp()
                        }
                    },
                    onSourceClick = { source ->
                        navController.runIfCurrent(backStackEntry) {
                            watchStateRepository.saveSelectedSource(
                                titleId = animeId,
                                sourceId = source.sourceId,
                                sourceTitle = source.title,
                                quality = source.qualityLabel,
                                autoSelect = false,
                            )
                            sharedNavigationState = sharedNavigationState.reduce(
                                AppNavigationEvent.Navigate(
                                    AppRoute.Episodes(source, downloadMode = downloadMode),
                                ),
                            )
                            navController.navigateSingleTopTo(
                                AnimeNavType.createEpisodesRoute(source, downloadMode = downloadMode)
                            )
                        }
                    },
                    modifier = screenModifier
                )
            }
        }
        composable(
            route = AnimeNavType.EPISODES_PATTERN,
            arguments = listOf(
                navArgument(AnimeNavType.SOURCE_ID_ARG) { type = NavType.StringType },
                navArgument(AnimeNavType.SOURCE_TITLE_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AnimeNavType.DOWNLOAD_MODE_ARG) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),
            enterTransition = { appScreenEnterTransition() },
            exitTransition = { appScreenExitTransition() },
            popEnterTransition = { appScreenPopEnterTransition() },
            popExitTransition = { appScreenPopExitTransition() }
        ) { backStackEntry ->
            val routeArgs = backStackEntry.arguments
            DestinationScreenContainer {
                EpisodesScreen(
                    sourceId = routeArgs.stringArg(AnimeNavType.SOURCE_ID_ARG),
                    sourceTitle = routeArgs.stringArg(AnimeNavType.SOURCE_TITLE_ARG),
                    downloadMode = routeArgs.booleanArg(AnimeNavType.DOWNLOAD_MODE_ARG),
                    onBackClick = {
                        navController.runIfCurrent(backStackEntry) {
                            sharedNavigationState = sharedNavigationState.reduce(AppNavigationEvent.Back)
                            navController.navigateUp()
                        }
                    },
                    onEpisodeClick = { episode ->
                        navController.runIfCurrent(backStackEntry) {
                            val sourceId = routeArgs.stringArg(AnimeNavType.SOURCE_ID_ARG)
                            sharedNavigationState = sharedNavigationState.reduce(
                                AppNavigationEvent.Navigate(
                                    AppRoute.Player(
                                        sourceId = sourceId,
                                        episodeId = episode.id,
                                        episodeNumber = episode.number,
                                    ),
                                ),
                            )
                            navController.navigateSingleTopTo(
                                AnimeNavType.createPlayerRoute(
                                    sourceId = sourceId,
                                    episodeId = episode.id,
                                    episodeNumber = episode.number,
                                )
                            )
                        }
                    },
                    modifier = screenModifier
                )
            }
        }
        composable(
            route = AnimeNavType.PLAYER_PATTERN,
            arguments = listOf(
                navArgument(AnimeNavType.SOURCE_ID_ARG) { type = NavType.StringType },
                navArgument(AnimeNavType.EPISODE_ID_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AnimeNavType.EPISODE_NUMBER_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            ),
            enterTransition = { appScreenEnterTransition() },
            exitTransition = { appScreenExitTransition() },
            popEnterTransition = { appScreenPopEnterTransition() },
            popExitTransition = { appScreenPopExitTransition() }
        ) { backStackEntry ->
            val routeArgs = backStackEntry.arguments
            DestinationScreenContainer {
                PlayerScreen(
                    sourceId = routeArgs.stringArg(AnimeNavType.SOURCE_ID_ARG),
                    episodeId = routeArgs.stringArg(AnimeNavType.EPISODE_ID_ARG),
                    episodeNumberHint = routeArgs.doubleArg(AnimeNavType.EPISODE_NUMBER_ARG),
                    onBackClick = {
                        navController.runIfCurrent(backStackEntry) {
                            sharedNavigationState = sharedNavigationState.reduce(AppNavigationEvent.Back)
                            navController.navigateUp()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TopLevelScreenContainer(
    destination: TopLevelDestination,
    destinations: List<TopLevelDestination>,
    onDestinationClick: (TopLevelDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    AppTopLevelScaffold(
        currentDestination = destination.toSharedDestination(),
        onDestinationClick = { onDestinationClick(it.toAndroidDestination()) },
        label = { sharedDestination ->
            stringResource(sharedDestination.toAndroidDestination().labelRes)
        },
        destinations = destinations.map { it.toSharedDestination() },
    ) {
        content()
    }
}

@Composable
private fun DestinationScreenContainer(
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
    }
}

private fun TopLevelDestination.toSharedDestination(): AppTopLevelDestination = when (this) {
    TopLevelDestination.Home -> AppTopLevelDestination.HOME
    TopLevelDestination.Catalog -> AppTopLevelDestination.CATALOG
    TopLevelDestination.Library -> AppTopLevelDestination.LIBRARY
    TopLevelDestination.Sources -> AppTopLevelDestination.SOURCES
    TopLevelDestination.Profile -> AppTopLevelDestination.PROFILE
}

private fun AppTopLevelDestination.toAndroidDestination(): TopLevelDestination = when (this) {
    AppTopLevelDestination.HOME -> TopLevelDestination.Home
    AppTopLevelDestination.CATALOG -> TopLevelDestination.Catalog
    AppTopLevelDestination.LIBRARY -> TopLevelDestination.Library
    AppTopLevelDestination.SOURCES -> TopLevelDestination.Sources
    AppTopLevelDestination.PROFILE -> TopLevelDestination.Profile
}

private fun NavHostController.navigateSingleTopTo(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

private inline fun NavHostController.runIfCurrent(
    backStackEntry: NavBackStackEntry,
    action: () -> Unit,
) {
    if (currentBackStackEntry?.id == backStackEntry.id) {
        action()
    }
}

private fun NavGraphBuilder.topLevelComposable(
    route: String,
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        content = { backStackEntry -> content(backStackEntry) },
    )
}

private fun NavHostController.navigateTopLevelDestination(
    currentTopLevel: TopLevelDestination,
    destination: TopLevelDestination,
) {
    if (currentDestination?.hierarchy?.any { it.route == destination.route } == true) return

    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun animeFromArguments(arguments: android.os.Bundle?): Anime {
    return Anime(
        id = arguments.stringArg(AnimeNavType.ID_ARG),
        title = arguments.stringArg(AnimeNavType.TITLE_ARG),
        subtitle = arguments.stringArg(AnimeNavType.SUBTITLE_ARG),
        episodesLabel = arguments.stringArg(AnimeNavType.EPISODES_ARG),
        status = arguments.stringArg(AnimeNavType.STATUS_ARG),
        posterUrl = arguments.stringArg(AnimeNavType.POSTER_ARG).ifBlank { null },
        posterFallbackUrl = arguments.stringArg(AnimeNavType.POSTER_FALLBACK_ARG).ifBlank { null }
    )
}

private fun android.os.Bundle?.stringArg(key: String): String = this?.getString(key).orEmpty()

private fun android.os.Bundle?.booleanArg(key: String, defaultValue: Boolean = false): Boolean {
    return this?.getBoolean(key) ?: defaultValue
}

private fun android.os.Bundle?.doubleArg(key: String): Double? {
    return this?.getString(key)?.toDoubleOrNull()
}
