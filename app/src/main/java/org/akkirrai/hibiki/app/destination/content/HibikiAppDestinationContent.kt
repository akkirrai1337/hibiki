package org.akkirrai.hibiki.app.destination.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.akkirrai.hibiki.app.destination.context.AppDestinationContentInput
import org.akkirrai.hibiki.app.destination.watch.isWatchRouteDriven
import org.akkirrai.hibiki.design.AppMotion
import org.akkirrai.hibiki.design.component.navigation.AppBottomBarContentExtraPadding
import org.akkirrai.hibiki.design.component.navigation.AppBottomBarHeight
import org.akkirrai.hibiki.layout.appBottomSystemInsetValue
import org.akkirrai.hibiki.app.navigation.AppDestination
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.core.source.AppSourceDescriptor
import org.akkirrai.hibiki.library.LibraryCategory
@Composable
internal fun AppDestinationTabContent(input: AppDestinationContentInput, tab: AppDestination) {
    val sourceState = input.sources.state
    val homeSourcesById = remember(sourceState.sources) { sourceState.sources.associateBy(AppSourceDescriptor::id) }
    // Collected here (not in the shell) so the shell's own recomposition scope is decoupled
    // from library changes -- this scope is still shared by every tab because Home's feed
    // needs libraryEntries and every tab's cards need libraryStatusByAnimeId, but it's a much
    // smaller blast radius than the root shell composable.
    val libraryUiState by input.library.state.libraryPresenter.state.collectAsStateWithLifecycle()
    val libraryEntries = libraryUiState.visibleEntries
    // Recent is a hidden bookkeeping flag, not a real category -- exclude it so it can never
    // shadow a title's actual library status badge on a card.
    val libraryStatusByAnimeId = remember(libraryEntries) {
        libraryEntries
            .filter { it.category != LibraryCategory.Recent }
            .associate { it.anime.id to it.category }
    }
    val bottomSystemInset = appBottomSystemInsetValue(input.platform.hostContext.includeNavigationBarPadding)
    AppDestinationTopLevelRoutes(
        input = input,
        selectedTab = tab,
        topLevelBottomContentPadding = if (input.watch.state.currentRoute is AppRoute.TopLevel) {
            AppBottomBarHeight + bottomSystemInset + AppBottomBarContentExtraPadding
        } else {
            bottomSystemInset
        },
        homeSourcesById = homeSourcesById,
        libraryStatusByAnimeId = libraryStatusByAnimeId,
        libraryEntries = libraryEntries,
    )
}

@Composable
internal fun AppDestinationContent(
    input: AppDestinationContentInput,
    routeOverride: AppRoute? = null,
) {
    val effectiveInput = input.withRouteOverride(routeOverride)
    val selectedTab = effectiveInput.selectedTab
    val contentState = effectiveInput.watch.state
    if (contentState.isWatchRouteDriven() && contentState.watchAnime != null) {
        AppDestinationWatchRoute(effectiveInput)
        return
    }

    // Details is a full-screen destination -- it hides Settings too, same as it hides the
    // tab NavHost (see AppProductionRoot's tabLayerVisible).
    val showSettingsOverlay = contentState.currentRoute !is AppRoute.Details

    if (showSettingsOverlay) {
        // AnimatedVisibility (fade only, matching appScreenTransition's own duration/style),
        // not AnimatedContent -- it only owns Settings' own subtree, so it can't cause the
        // outer root transition and this one to fight over the same content the way two
        // separate AnimatedContents did before. Profile stays mounted underneath the whole
        // time (the root transition treats Profile<->Settings as one continuous slot), so this
        // is the only screen-enter animation Settings gets, and it doesn't touch Profile.
        AnimatedVisibility(
            visible = selectedTab == AppDestination.SETTINGS,
            enter = fadeIn(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis)),
            exit = fadeOut(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                AppDestinationTabContent(input = effectiveInput, tab = AppDestination.SETTINGS)
            }
        }
    }

    // Keyed on the title id, not the Anime object itself: the details fetch replaces
    // selectedAnime with a richer object once it resolves, and keying on the full object
    // would make AnimatedContent treat that data refresh as a brand new navigation target,
    // replaying the whole screen transition for a title the user never left.
    val detailsAnimeId = contentState.selectedAnime?.id
        ?.takeIf { contentState.currentRoute is org.akkirrai.hibiki.app.navigation.AppRoute.Details }
    // The outer root already owns the route transition. Rendering Details directly avoids a
    // second transition container retaining and measuring another full-screen subtree.
    val anime = contentState.selectedAnime?.takeIf { it.id == detailsAnimeId }
    if (anime != null) {
        AppDestinationDetailsRoute(input = effectiveInput, animeOverride = anime)
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
