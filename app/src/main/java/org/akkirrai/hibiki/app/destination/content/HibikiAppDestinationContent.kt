package org.akkirrai.hibiki.app.destination.content

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
import org.akkirrai.hibiki.app.destination.context.AppDestinationContentInput
import org.akkirrai.hibiki.app.destination.watch.isWatchRouteDriven
import org.akkirrai.hibiki.design.component.navigation.AppBottomBarContentExtraPadding
import org.akkirrai.hibiki.design.component.navigation.AppBottomBarHeight
import org.akkirrai.hibiki.layout.appBottomSystemInsetValue
import org.akkirrai.hibiki.app.navigation.AppDestination
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.core.source.AppSourceDescriptor
import org.akkirrai.hibiki.core.source.LocalAppSourceConfigContent
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
        // No animation of its own -- same reasoning as details_route_transition below: the
        // outer root transition already fades every entry/exit of Settings.
        AnimatedContent(
            targetState = selectedTab == AppDestination.SETTINGS,
            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
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

    // Keyed on the title id, not the Anime object itself: the details fetch replaces
    // selectedAnime with a richer object once it resolves, and keying on the full object
    // would make AnimatedContent treat that data refresh as a brand new navigation target,
    // replaying the whole screen transition for a title the user never left.
    val detailsAnimeId = contentState.selectedAnime?.id
        ?.takeIf { contentState.currentRoute is org.akkirrai.hibiki.app.navigation.AppRoute.Details }
    // No animation of its own -- the outer root transition in AppProductionRoot already
    // fades every entry/exit of the Details route (its route field changes on every visit).
    // Animating here too would stack a second, independent crossfade on top of that one.
    AnimatedContent(
        targetState = detailsAnimeId,
        transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        label = "details_route_transition",
        modifier = Modifier.fillMaxSize(),
    ) { targetId ->
        val anime = contentState.selectedAnime?.takeIf { it.id == targetId }
        if (anime != null) {
            AppDestinationDetailsRoute(input = effectiveInput, animeOverride = anime)
        }
    }
}

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
