package org.akkirrai.hibiki.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.log.PerfLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.feature.account.YummyAccountScreen
import org.akkirrai.hibiki.feature.account.YummyAccountSettingsScreen
import org.akkirrai.hibiki.feature.account.YummyAccountViewModel
import org.akkirrai.hibiki.feature.details.DetailsScreen
import org.akkirrai.hibiki.feature.home.HomeScreen
import org.akkirrai.hibiki.feature.home.HomeSearchFiltersScreen
import org.akkirrai.hibiki.feature.home.HomeViewModel
import org.akkirrai.hibiki.feature.home.TrendingAnimeScreen
import org.akkirrai.hibiki.feature.library.LibraryScreen
import org.akkirrai.hibiki.feature.player.EpisodesScreen
import org.akkirrai.hibiki.feature.player.PlayerScreen
import org.akkirrai.hibiki.feature.player.WatchSourcesScreen
import org.akkirrai.hibiki.feature.settings.SettingsScreen
import org.akkirrai.hibiki.core.source.WatchStateRepository

@Composable
fun HibikiApp(
    appPreferences: AppPreferences? = null,
) {
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topLevelBottomContentPadding = BottomBarHeight + navigationBarBottomPadding + BottomBarContentExtraPadding
    val navController = rememberNavController()
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
    LaunchedEffect(currentRoute) {
        AppLogger.setContext("route", currentRoute ?: "<none>")
        AppLogger.setContext("topLevelRoute", currentTopLevel.route)
        PerfLogger.mark(
            event = "Navigation route changed",
            details = "route=$currentRoute, topLevel=$isTopLevelDestination",
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                if (isTopLevelDestination) {
                    AppBottomBar(
                        destinations = destinations,
                        currentTopLevel = currentTopLevel,
                        onDestinationClick = { destination ->
                            navController.navigateTopLevelDestination(
                                currentTopLevel = currentTopLevel,
                                destination = destination,
                            )
                        },
                    )
                }
            },
        ) { innerPadding ->
            HibikiNavHost(
                modifier = Modifier.fillMaxSize(),
                navController = navController,
                contentPadding = innerPadding,
                topLevelBottomContentPadding = topLevelBottomContentPadding,
                appPreferences = appPreferences,
                showBottomBar = isTopLevelDestination,
                currentTopLevel = currentTopLevel,
            )
        }
    }
}

@Composable
private fun HibikiNavHost(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    topLevelBottomContentPadding: Dp = BottomBarHeight + BottomBarContentExtraPadding,
    appPreferences: AppPreferences? = null,
    showBottomBar: Boolean = false,
    currentTopLevel: TopLevelDestination = TopLevelDestination.Home,
) {
    val baseScreenModifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)
        .padding(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() +
                if (showBottomBar) {
                    BottomBarContentExtraPadding
                } else {
                    0.dp
                }
        )
        .consumeWindowInsets(contentPadding)

    val topLevelScreenModifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)
        .padding(top = contentPadding.calculateTopPadding())
        .consumeWindowInsets(contentPadding)

    val screenModifier = baseScreenModifier.statusBarsPadding()

    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Home.route,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .clipToBounds()
    ) {
        composable(
            route = TopLevelDestination.Home.route,
            enterTransition = {
                topLevelEnterTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route,
                )
            },
            exitTransition = {
                topLevelExitTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route,
                )
            },
            popEnterTransition = {
                topLevelEnterTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route,
                )
            },
            popExitTransition = {
                topLevelExitTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route,
                )
            },
        ) { backStackEntry ->
            val context = LocalContext.current
            val dependencies = remember(context) { context.applicationContext.hibikiDependencies() }
            val homeViewModel: HomeViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = HomeViewModel.Factory(context),
            )
            HomeScreen(
                viewModel = homeViewModel,
                onAnimeClick = { anime ->
                    navController.navigate(AnimeNavType.createDetailsRoute(anime))
                },
                onShowAllTrendingClick = {
                    navController.navigateSingleTopTo(AnimeNavType.TRENDING_ROUTE)
                },
                onProfileClick = {
                    navController.navigateSingleTopTo(AnimeNavType.ACCOUNT_ROUTE)
                },
                onFilterClick = {
                    navController.navigateSingleTopTo(AnimeNavType.SEARCH_FILTERS_ROUTE)
                },
                isActive = showBottomBar && currentTopLevel == TopLevelDestination.Home,
                bottomContentPadding = topLevelBottomContentPadding,
                modifier = topLevelScreenModifier
            )
        }
        composable(
            route = AnimeNavType.SEARCH_FILTERS_ROUTE,
            enterTransition = { appScreenEnterTransition() },
            exitTransition = { appScreenExitTransition() },
            popEnterTransition = { appScreenPopEnterTransition() },
            popExitTransition = { appScreenPopExitTransition() },
        ) {
            val context = LocalContext.current
            val dependencies = remember(context) { context.applicationContext.hibikiDependencies() }
            val homeEntry = remember(navController) {
                navController.getBackStackEntry(TopLevelDestination.Home.route)
            }
            val homeViewModel: HomeViewModel = viewModel(
                viewModelStoreOwner = homeEntry,
                factory = HomeViewModel.Factory(context),
            )
            HomeSearchFiltersScreen(
                viewModel = homeViewModel,
                onBackClick = navController::navigateUp,
                modifier = topLevelScreenModifier.statusBarsPadding(),
            )
        }
        composable(
            route = AnimeNavType.ACCOUNT_ROUTE,
            enterTransition = { appScreenEnterTransition() },
            exitTransition = { appScreenExitTransition() },
            popEnterTransition = { appScreenPopEnterTransition() },
            popExitTransition = { appScreenPopExitTransition() }
        ) { backStackEntry ->
            val context = LocalContext.current
            val accountViewModel: YummyAccountViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = YummyAccountViewModel.Factory(context),
            )
            YummyAccountScreen(
                onBackClick = navController::navigateUp,
                onSettingsClick = {
                    navController.navigateSingleTopTo(AnimeNavType.ACCOUNT_SETTINGS_ROUTE)
                },
                modifier = screenModifier,
                viewModel = accountViewModel,
            )
        }
        composable(
            route = AnimeNavType.ACCOUNT_SETTINGS_ROUTE,
            enterTransition = { appScreenEnterTransition() },
            exitTransition = { appScreenExitTransition() },
            popEnterTransition = { appScreenPopEnterTransition() },
            popExitTransition = { appScreenPopExitTransition() }
        ) {
            val context = LocalContext.current
            val accountEntry = remember(navController) {
                navController.getBackStackEntry(AnimeNavType.ACCOUNT_ROUTE)
            }
            val accountViewModel: YummyAccountViewModel = viewModel(
                viewModelStoreOwner = accountEntry,
                factory = YummyAccountViewModel.Factory(context),
            )
            YummyAccountSettingsScreen(
                onBackClick = navController::navigateUp,
                modifier = screenModifier,
                viewModel = accountViewModel,
            )
        }
        composable(
            route = TopLevelDestination.Library.route,
            enterTransition = {
                topLevelEnterTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route,
                )
            },
            exitTransition = {
                topLevelExitTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route,
                )
            },
            popEnterTransition = {
                topLevelEnterTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route,
                )
            },
            popExitTransition = {
                topLevelExitTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route,
                )
            },
        ) {
            LibraryScreen(
                onAnimeClick = { anime ->
                    navController.navigate(AnimeNavType.createDetailsRoute(anime))
                },
                onProfileClick = {
                    navController.navigateSingleTopTo(AnimeNavType.ACCOUNT_ROUTE)
                },
                isActive = showBottomBar && currentTopLevel == TopLevelDestination.Library,
                bottomContentPadding = topLevelBottomContentPadding,
                modifier = topLevelScreenModifier
            )
        }
        composable(
            route = TopLevelDestination.Settings.route,
            enterTransition = {
                topLevelEnterTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route,
                )
            },
            exitTransition = {
                topLevelExitTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route,
                )
            },
            popEnterTransition = {
                topLevelEnterTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route,
                )
            },
            popExitTransition = {
                topLevelExitTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route,
                )
            },
        ) {
            SettingsScreen(
                modifier = topLevelScreenModifier.statusBarsPadding(),
                bottomContentPadding = topLevelBottomContentPadding,
                appPreferences = appPreferences,
            )
        }
        composable(
            route = AnimeNavType.TRENDING_ROUTE,
            enterTransition = { appScreenEnterTransition() },
            exitTransition = { appScreenExitTransition() },
            popEnterTransition = { appScreenPopEnterTransition() },
            popExitTransition = { appScreenPopExitTransition() }
        ) {
            TrendingAnimeScreen(
                onBackClick = navController::navigateUp,
                onAnimeClick = { anime ->
                    navController.navigate(AnimeNavType.createDetailsRoute(anime))
                },
                modifier = screenModifier,
            )
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
            DetailsScreen(
                anime = animeFromArguments(backStackEntry.arguments),
                onBackClick = navController::navigateUp,
                onRelatedAnimeClick = { anime ->
                    navController.navigate(AnimeNavType.createDetailsRoute(anime))
                },
                onOpenSources = { anime ->
                    navController.navigateSingleTopTo(AnimeNavType.createWatchSourcesRoute(anime))
                },
                onOpenDownloadSources = { anime ->
                    navController.navigateSingleTopTo(AnimeNavType.createWatchSourcesRoute(anime, downloadMode = true))
                },
                contentPadding = contentPadding
            )
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
            val animeId = backStackEntry.arguments?.getString(AnimeNavType.ID_ARG).orEmpty()
            val downloadMode = backStackEntry.arguments?.getBoolean(AnimeNavType.DOWNLOAD_MODE_ARG) ?: false
            WatchSourcesScreen(
                animeId = animeId,
                animeTitle = backStackEntry.arguments?.getString(AnimeNavType.TITLE_ARG).orEmpty(),
                onBackClick = navController::navigateUp,
                onSourceClick = { source ->
                    watchStateRepository.saveSelectedSource(
                        titleId = animeId,
                        sourceId = source.sourceId,
                        sourceTitle = source.title,
                        quality = source.qualityLabel,
                        autoSelect = false,
                    )
                    navController.navigateSingleTopTo(AnimeNavType.createEpisodesRoute(source, downloadMode = downloadMode))
                },
                modifier = screenModifier
            )
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
            EpisodesScreen(
                sourceId = backStackEntry.arguments?.getString(AnimeNavType.SOURCE_ID_ARG).orEmpty(),
                sourceTitle = backStackEntry.arguments?.getString(AnimeNavType.SOURCE_TITLE_ARG).orEmpty(),
                downloadMode = backStackEntry.arguments?.getBoolean(AnimeNavType.DOWNLOAD_MODE_ARG) ?: false,
                onBackClick = navController::navigateUp,
                onEpisodeClick = { episode ->
                    val sourceId = backStackEntry.arguments?.getString(AnimeNavType.SOURCE_ID_ARG).orEmpty()
                    navController.navigateSingleTopTo(AnimeNavType.createPlayerRoute(sourceId, episode.id))
                },
                modifier = screenModifier
            )
        }
        composable(
            route = AnimeNavType.PLAYER_PATTERN,
            arguments = listOf(
                navArgument(AnimeNavType.SOURCE_ID_ARG) { type = NavType.StringType },
                navArgument(AnimeNavType.EPISODE_ID_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            ),
            enterTransition = { appScreenEnterTransition() },
            exitTransition = { appScreenExitTransition() },
            popEnterTransition = { appScreenPopEnterTransition() },
            popExitTransition = { appScreenPopExitTransition() }
        ) { backStackEntry ->
            PlayerScreen(
                sourceId = backStackEntry.arguments?.getString(AnimeNavType.SOURCE_ID_ARG).orEmpty(),
                episodeId = backStackEntry.arguments?.getString(AnimeNavType.EPISODE_ID_ARG).orEmpty(),
                onBackClick = navController::navigateUp,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private val BottomBarHeight = 64.dp
private val BottomBarHorizontalPadding = 14.dp
private val BottomBarVerticalPadding = 6.dp
private val BottomBarItemHeight = 48.dp
private val BottomBarActivePillWidth = 68.dp
private val BottomBarActivePillHeight = 30.dp
private val BottomBarIconSize = 22.dp
private val BottomBarLabelSize = 11.sp
private val BottomBarContentExtraPadding = 12.dp

@Composable
private fun AppBottomBar(
    destinations: List<TopLevelDestination>,
    currentTopLevel: TopLevelDestination,
    onDestinationClick: (TopLevelDestination) -> Unit,
) {
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = navigationBarBottomPadding),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BottomBarHeight)
                    .padding(
                        horizontal = BottomBarHorizontalPadding,
                        vertical = BottomBarVerticalPadding,
                    ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    AppBottomBarItem(
                        destination = destination,
                        selected = currentTopLevel == destination,
                        onClick = { onDestinationClick(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBottomBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pillShape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .height(BottomBarItemHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(
                    width = BottomBarActivePillWidth,
                    height = BottomBarActivePillHeight,
                )
                .clip(pillShape),
            shape = pillShape,
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
            contentColor = contentColor,
            tonalElevation = if (selected) 2.dp else 0.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = stringResource(destination.labelRes),
                    modifier = Modifier.size(BottomBarIconSize),
                    tint = contentColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = stringResource(destination.labelRes),
            fontSize = BottomBarLabelSize,
            lineHeight = BottomBarLabelSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                contentColor
            },
            maxLines = 1,
        )
    }
}

private fun NavHostController.navigateSingleTopTo(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

private fun NavHostController.navigateTopLevelDestination(
    currentTopLevel: TopLevelDestination,
    destination: TopLevelDestination,
) {
    if (currentTopLevel == destination) return

    navigate(destination.route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
    }
}

private fun animeFromArguments(arguments: android.os.Bundle?): Anime {
    return Anime(
        id = arguments?.getString(AnimeNavType.ID_ARG).orEmpty(),
        title = arguments?.getString(AnimeNavType.TITLE_ARG).orEmpty(),
        subtitle = arguments?.getString(AnimeNavType.SUBTITLE_ARG).orEmpty(),
        episodesLabel = arguments?.getString(AnimeNavType.EPISODES_ARG).orEmpty(),
        status = arguments?.getString(AnimeNavType.STATUS_ARG).orEmpty(),
        posterUrl = arguments?.getString(AnimeNavType.POSTER_ARG).orEmpty().ifBlank { null },
        posterFallbackUrl = arguments?.getString(AnimeNavType.POSTER_FALLBACK_ARG).orEmpty().ifBlank { null }
    )
}
