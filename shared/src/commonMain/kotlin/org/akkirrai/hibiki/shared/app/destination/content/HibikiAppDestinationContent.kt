package org.akkirrai.hibiki.shared.app.destination.content

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.app.destination.context.AppDestinationContentInput
import org.akkirrai.hibiki.shared.app.shell.layout.appScreenTransition
import org.akkirrai.hibiki.shared.design.component.navigation.AppBottomBarContentExtraPadding
import org.akkirrai.hibiki.shared.design.component.navigation.AppBottomBarHeight
import org.akkirrai.hibiki.shared.layout.appBottomSystemInsetValue
import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.navigation.AppTransitionDirection
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.LocalAppSourceConfigContent
@Composable
internal fun AppDestinationContent(input: AppDestinationContentInput) {
    val selectedTab = input.selectedTab
    val contentState = input.watch.state
    val sourceState = input.sources.state
    val hostContext = input.platform.hostContext
    var editingSourceConfig by remember { mutableStateOf<AppSourceDescriptor?>(null) }
    val sourceConfigContent = LocalAppSourceConfigContent.current
    val homeSourcesById = remember(sourceState.sources) { sourceState.sources.associateBy(AppSourceDescriptor::id) }
    val bottomSystemInset = appBottomSystemInsetValue(hostContext.includeNavigationBarPadding)
    if (contentState.isWatchRouteDriven() && contentState.watchAnime != null) {
        AppDestinationWatchRoute(input)
        return
    }

    val baseSelectedTab = if (selectedTab == AppDestination.SETTINGS) {
        AppDestination.PROFILE
    } else {
        selectedTab
    }

    AppDestinationTopLevelRoutes(
        input = input,
        selectedTab = baseSelectedTab,
        topLevelBottomContentPadding = if (
            baseSelectedTab != AppDestination.SETTINGS && contentState.currentRoute is AppRoute.TopLevel
        ) {
            AppBottomBarHeight + bottomSystemInset + AppBottomBarContentExtraPadding
        } else {
            bottomSystemInset
        },
        homeSourcesById = homeSourcesById,
        editingSourceConfig = editingSourceConfig,
        sourceConfigContent = sourceConfigContent,
        onEditSourceConfig = { editingSourceConfig = it },
    )

    AnimatedContent(
        targetState = selectedTab == AppDestination.SETTINGS,
        transitionSpec = { appScreenTransition(AppTransitionDirection.Forward) },
        label = "settings_route_transition",
        modifier = Modifier.fillMaxSize(),
    ) { settingsVisible ->
        if (settingsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                AppDestinationTopLevelRoutes(
                    input = input,
                    selectedTab = AppDestination.SETTINGS,
                    topLevelBottomContentPadding = bottomSystemInset,
                    homeSourcesById = homeSourcesById,
                    editingSourceConfig = editingSourceConfig,
                    sourceConfigContent = sourceConfigContent,
                    onEditSourceConfig = { editingSourceConfig = it },
                )
            }
        }
    }

    val detailsTarget = DetailsLayerTarget(
        anime = contentState.selectedAnime.takeIf { contentState.currentRoute is org.akkirrai.hibiki.shared.navigation.AppRoute.Details },
    )
    AnimatedContent(
        targetState = detailsTarget,
        transitionSpec = {
            when {
                initialState.anime != null && targetState.anime != null ->
                    EnterTransition.None togetherWith ExitTransition.None
                targetState.anime != null ->
                    appScreenTransition(AppTransitionDirection.Forward)
                else ->
                    appScreenTransition(AppTransitionDirection.Pop)
            }
        },
        label = "details_route_transition",
        modifier = Modifier.fillMaxSize(),
    ) { target ->
        target.anime?.let { anime ->
            AppDestinationDetailsRoute(input = input, animeOverride = anime)
        }
    }
}

private data class DetailsLayerTarget(val anime: Anime?)
