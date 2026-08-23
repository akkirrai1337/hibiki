package org.akkirrai.hibiki.app.destination.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.app.destination.context.AppDestinationContentInput
import org.akkirrai.hibiki.app.destination.home.*
import org.akkirrai.hibiki.app.destination.library.*
import org.akkirrai.hibiki.app.destination.settings.*
import org.akkirrai.hibiki.app.destination.source.*
import org.akkirrai.hibiki.app.settings.SettingsRoute
import org.akkirrai.hibiki.app.shell.navigation.shouldApplyTopSystemInset
import org.akkirrai.hibiki.home.screen.HomeRoute
import org.akkirrai.hibiki.home.screen.CatalogRoute
import org.akkirrai.hibiki.layout.appTopSystemInsetPadding
import org.akkirrai.hibiki.library.screen.LibraryRoute
import org.akkirrai.hibiki.profile.ProfileRoute
import org.akkirrai.hibiki.app.navigation.AppDestination
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.core.source.AppSourceDescriptor

@Composable
internal fun AppDestinationTopLevelRoutes(
    input: AppDestinationContentInput,
    selectedTab: AppDestination,
    topLevelBottomContentPadding: Dp,
    homeSourcesById: Map<String, AppSourceDescriptor>,
    libraryStatusByAnimeId: Map<String, org.akkirrai.hibiki.library.LibraryCategory>,
    libraryEntries: List<org.akkirrai.hibiki.library.LibraryEntry>,
) {
    val catalog = input.catalog
    val home = input.home
    val library = input.library
    val settings = input.settings
    val sources = input.sources
    val profile = input.profile
    val platform = input.platform
    val navigation = input.navigation
    val content = input.watch.state
    val topInsetModifier = Modifier.appTopSystemInsetPadding()

    Column(
        modifier = platform.hostContext.modifier
            .fillMaxSize()
            .then(if (shouldApplyTopSystemInset(selectedTab)) topInsetModifier else Modifier),
    ) {
        when (selectedTab) {
            AppDestination.HOME -> HomeRoute(
                homePresenter = home.state.homePresenter,
                homeSearchPresenter = home.state.homeSearchPresenter,
                listState = home.state.listState,
                sourcesById = homeSourcesById,
                libraryStatusByAnimeId = libraryStatusByAnimeId,
                libraryEntries = libraryEntries,
                actions = home.actions,
                onHomeRefresh = home.onRefresh,
                bottomContentPadding = topLevelBottomContentPadding,
            )
            AppDestination.CATALOG -> CatalogRoute(
                presenter = catalog.presenter,
                listState = catalog.listState,
                libraryStatusByAnimeId = libraryStatusByAnimeId,
                actions = catalog.actions,
                bottomContentPadding = topLevelBottomContentPadding,
            )
            AppDestination.LIBRARY -> LibraryRoute(
                sources = sources.state.sources,
                libraryPresenter = library.state.libraryPresenter,
                actions = library.actions,
                listState = library.state.listState,
                onFiltersApply = library.onFiltersApply,
                filterOverlayOpen = library.state.filterOverlayOpen,
                languageMode = platform.dataContext.languageMode,
                systemLanguage = platform.hostContext.systemLanguage,
                bottomContentPadding = topLevelBottomContentPadding,
            )
            AppDestination.PROFILE -> ProfileRoute(
                profilePresenter = profile.profilePresenter,
                profileRepository = platform.dataContext.profileRepository,
                avatarEditAvailable = profile.avatarEditAvailable,
                onAvatarEdit = profile.onAvatarEdit,
                onSettingsClick = navigation.actions.onProfileSettingsClick,
                languageMode = platform.dataContext.languageMode,
                systemLanguage = platform.hostContext.systemLanguage,
                bottomContentPadding = topLevelBottomContentPadding,
                modifier = Modifier.fillMaxSize(),
            )
            AppDestination.SOURCES -> SourcesRoute(
                state = SourcesRouteState(
                    sources = sources.state.sources,
                    selectedSourceId = sources.state.selectedSourceId,
                    currentRoute = content.currentRoute
                        ?: AppRoute.TopLevel(org.akkirrai.hibiki.app.navigation.AppTopLevelDestination.SOURCES),
                    selectedSourcesTab = sources.externalSourcesState.selectedTab,
                ),
                actions = SourcesRouteActions(
                    onSourceSelected = navigation.actions.onSourceSelected,
                    onSelectedSourcesTabChange = sources.externalSourcesState.onSelectedTabChange,
                    onOpenPackageInfo = navigation.actions.onSourcePackageInfoClick,
                    onBack = navigation.actions.onSettingsBack,
                ),
                externalSourcesController = sources.externalSourcesState.controller,
                sourceConfigContent = sources.sourceConfigContent,
                bottomContentPadding = topLevelBottomContentPadding,
            )
            AppDestination.SETTINGS -> SettingsRoute(
                state = settings.state,
                actions = settings.actions,
                listState = settings.listsState.settings,
                bottomContentPadding = topLevelBottomContentPadding,
            )
        }
    }
}
