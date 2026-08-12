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
internal fun AppDestinationContent(
    input: AppDestinationContentInput,
    routeOverride: AppRoute? = null,
) {
    val effectiveInput = input.withRouteOverride(routeOverride)
    val selectedTab = effectiveInput.selectedTab
    val contentState = effectiveInput.watch.state
    val sourceState = effectiveInput.sources.state
    val hostContext = effectiveInput.platform.hostContext
    var editingSourceConfig by remember { mutableStateOf<AppSourceDescriptor?>(null) }
    val sourceConfigContent = LocalAppSourceConfigContent.current
    val homeSourcesById = remember(sourceState.sources) { sourceState.sources.associateBy(AppSourceDescriptor::id) }
    val bottomSystemInset = appBottomSystemInsetValue(hostContext.includeNavigationBarPadding)
    if (contentState.isWatchRouteDriven() && contentState.watchAnime != null) {
        AppDestinationWatchRoute(effectiveInput)
        return
    }

    val baseSelectedTab = if (selectedTab == AppDestination.SETTINGS) {
        AppDestination.PROFILE
    } else {
        selectedTab
    }

    // Details is a full-screen destination. Do not keep composing the whole
    // underlying top-level screen while the root transition is already
    // retaining the outgoing screen for its fade-out.
    val showBaseRoutes = contentState.currentRoute !is AppRoute.Details
    if (showBaseRoutes) {
        AppDestinationTopLevelRoutes(
            input = effectiveInput,
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
    }

    if (showBaseRoutes) {
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
                        input = effectiveInput,
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
                else -> EnterTransition.None togetherWith ExitTransition.None
            }
        },
        label = "details_route_transition",
        modifier = Modifier.fillMaxSize(),
    ) { target ->
        target.anime?.let { anime ->
            AppDestinationDetailsRoute(input = effectiveInput, animeOverride = anime)
        }
    }
}

private data class DetailsLayerTarget(val anime: Anime?)

private fun AppDestinationContentInput.withRouteOverride(route: AppRoute?): AppDestinationContentInput {
    if (route == null || route == watch.state.currentRoute) return this
    return AppDestinationContentInput(
        selectedTab = selectedTab,
        catalog = catalog,
        home = home,
        library = library,
        settings = settings,
        sources = sources,
        watch = watch.copy(state = watch.state.copy(currentRoute = route)),
        profile = profile,
        platform = platform,
        navigation = navigation,
    )
}
