package org.akkirrai.hibiki.app.destination.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.app.destination.context.AppDestinationContentInput
import org.akkirrai.hibiki.app.destination.home.*
import org.akkirrai.hibiki.app.destination.library.*
import org.akkirrai.hibiki.app.destination.profile.*
import org.akkirrai.hibiki.app.destination.settings.*
import org.akkirrai.hibiki.app.destination.source.*
import org.akkirrai.hibiki.app.settings.SettingsRoute
import org.akkirrai.hibiki.app.shell.navigation.shouldApplyTopSystemInset
import org.akkirrai.hibiki.home.screen.HomeRoute
import org.akkirrai.hibiki.home.screen.CatalogRoute
import org.akkirrai.hibiki.layout.appTopSystemInsetPadding
import org.akkirrai.hibiki.library.screen.LibraryRoute
import org.akkirrai.hibiki.profile.ProfileDestinationContent
import org.akkirrai.hibiki.app.navigation.AppDestination
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.core.source.AppSourceConfigContent
import org.akkirrai.hibiki.core.source.AppSourceDescriptor

@Composable
internal fun AppDestinationTopLevelRoutes(
    input: AppDestinationContentInput,
    selectedTab: AppDestination,
    topLevelBottomContentPadding: Dp,
    homeSourcesById: Map<String, AppSourceDescriptor>,
    editingSourceConfig: AppSourceDescriptor?,
    sourceConfigContent: AppSourceConfigContent?,
    onEditSourceConfig: (AppSourceDescriptor?) -> Unit,
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
    // Recent is a hidden bookkeeping flag, not a real category -- exclude it so it can never
    // shadow a title's actual library status badge on a card.
    val libraryStatusByAnimeId = library.state.entries
        .filter { it.category != org.akkirrai.hibiki.library.LibraryCategory.Recent }
        .associate { it.anime.id to it.category }

    Column(
        modifier = platform.hostContext.modifier
            .fillMaxSize()
            .then(if (shouldApplyTopSystemInset(selectedTab)) topInsetModifier else Modifier),
    ) {
        when (selectedTab) {
            AppDestination.HOME -> HomeRoute(
                listState = home.state.listState,
                sourcesById = homeSourcesById,
                libraryEntries = library.state.entries,
                libraryStatusByAnimeId = libraryStatusByAnimeId,
                onQueryChange = home.actions.onQueryChange,
                homeSearchState = home.state.search,
                onFilterApply = home.actions.onFilterApply,
                onSearchClear = home.actions.onSearchClear,
                onSearchLoadMore = home.actions.onSearchLoadMore,
                onSearchRetry = home.actions.onSearchRetry,
                onAnimeClick = navigation.actions.onAnimeClick,
                onBrowseCatalog = navigation.actions.onBrowseCatalog,
                onOpenLibrary = navigation.actions.onOpenLibrary,
                baseHomeState = home.state.ui,
                onItemVisible = home.actions.onItemVisible,
                onHomeRefresh = home.actions.onRefresh,
                bottomContentPadding = topLevelBottomContentPadding,
            )
            AppDestination.CATALOG -> CatalogRoute(
                state = catalog.state,
                listState = catalog.listState,
                libraryStatusByAnimeId = libraryStatusByAnimeId,
                actions = catalog.actions,
                bottomContentPadding = topLevelBottomContentPadding,
            )
            AppDestination.LIBRARY -> LibraryRoute(
                sources = sources.state.sources,
                state = library.state.ui,
                onAnimeClick = navigation.actions.onAnimeClick,
                onCategorySelected = library.actions.onCategorySelected,
                onSearchQueryChange = library.actions.onSearchQueryChange,
                onSearchClear = library.actions.onSearchClear,
                onFiltersApply = library.actions.onFiltersApply,
                filterOverlayOpen = library.state.filterOverlayOpen,
                onFilterOpen = library.actions.onFilterOpen,
                onFilterVisibilityChange = library.actions.onFilterVisibilityChange,
                languageMode = platform.dataContext.languageMode,
                systemLanguage = platform.hostContext.systemLanguage,
                bottomContentPadding = topLevelBottomContentPadding,
            )
            AppDestination.PROFILE -> ProfileDestinationContent(
                profileData = profile.state.data,
                profileLoading = profile.state.isLoading,
                profileAvatarEditAvailable = profile.state.avatarEditAvailable,
                isEditingProfile = profile.state.isEditing,
                editedProfileName = profile.state.editedName,
                languageMode = platform.dataContext.languageMode,
                systemLanguage = platform.hostContext.systemLanguage,
                bottomContentPadding = topLevelBottomContentPadding,
                onProfileNameChange = profile.actions.onNameChange,
                onProfileEditClick = profile.actions.onEditClick,
                onProfileSaveClick = profile.actions.onSaveClick,
                onProfileSettingsClick = navigation.actions.onProfileSettingsClick,
                onProfileAvatarEdit = profile.actions.onAvatarEdit,
                onProfileAvatarPicked = profile.actions.onAvatarPicked,
                modifier = Modifier.fillMaxSize(),
            )
            AppDestination.SOURCES -> SourcesRoute(
                editingSourceConfig = editingSourceConfig,
                sourceConfigContent = sourceConfigContent,
                sources = sources.state.sources,
                selectedSourceId = sources.state.selectedSourceId,
                sourceSearchState = sources.state.search,
                bottomContentPadding = topLevelBottomContentPadding,
                onSourceSelected = navigation.actions.onSourceSelected,
                onEditSourceConfig = onEditSourceConfig,
                onSourceConfigSaved = { source ->
                    navigation.actions.onSourceSelected(source.id)
                    onEditSourceConfig(null)
                },
                onSourceConfigCancel = { onEditSourceConfig(null) },
                onSourceSearchQueryChange = sources.searchActions.onQueryChange,
                onSourceSearchClear = sources.searchActions.onClear,
                onSourceSearchRetry = sources.searchActions.onRetry,
                onSearchRetryForSource = sources.searchActions.onRetryForSource,
                onAnimeClick = navigation.actions.onAnimeClick,
                currentRoute = content.currentRoute ?: AppRoute.TopLevel(org.akkirrai.hibiki.app.navigation.AppTopLevelDestination.SOURCES),
                externalSourcesState = sources.externalSourcesState.repository,
                externalSourcesController = sources.externalSourcesState.controller,
                selectedSourcesTab = sources.externalSourcesState.selectedTab,
                onSelectedSourcesTabChange = sources.externalSourcesState.onSelectedTabChange,
                onOpenRepositories = navigation.actions.onSourceRepositoriesClick,
                onOpenPackageInfo = navigation.actions.onSourcePackageInfoClick,
                onBack = navigation.actions.onSettingsBack,
                onOpenUrl = platform.hostContext.onOpenUrl,
                readClipboardText = sources.externalSourcesState.readClipboardText,
                copyText = sources.externalSourcesState.copyText,
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
