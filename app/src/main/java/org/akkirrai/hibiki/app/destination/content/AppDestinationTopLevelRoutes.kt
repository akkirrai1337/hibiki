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
    libraryStatusByAnimeId: Map<String, org.akkirrai.hibiki.library.LibraryCategory>,
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

    Column(
        modifier = platform.hostContext.modifier
            .fillMaxSize()
            .then(if (shouldApplyTopSystemInset(selectedTab)) topInsetModifier else Modifier),
    ) {
        when (selectedTab) {
            AppDestination.HOME -> HomeRoute(
                baseHomeState = home.state.ui,
                listState = home.state.listState,
                sourcesById = homeSourcesById,
                libraryStatusByAnimeId = libraryStatusByAnimeId,
                libraryEntries = library.state.entries,
                homeSearchState = home.state.search,
                actions = home.actions,
                onHomeRefresh = home.onRefresh,
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
                actions = library.actions,
                onFiltersApply = library.onFiltersApply,
                filterOverlayOpen = library.state.filterOverlayOpen,
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
