package org.akkirrai.hibiki.shared.app.destination.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.app.destination.context.AppDestinationContentInput
import org.akkirrai.hibiki.shared.app.destination.routes.*
import org.akkirrai.hibiki.shared.app.shell.navigation.shouldApplyTopSystemInset
import org.akkirrai.hibiki.shared.layout.appTopSystemInsetPadding
import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.source.AppSourceConfigContent
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

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

    Column(
        modifier = platform.hostContext.modifier
            .fillMaxSize()
            .then(if (shouldApplyTopSystemInset(selectedTab)) topInsetModifier else Modifier),
    ) {
        when (selectedTab) {
            AppDestination.HOME -> HomeDestinationRoute(
                state = catalog.state.ui,
                listState = catalog.state.listState,
                sourcesById = homeSourcesById,
                libraryEntries = library.state.entries,
                libraryStatusByAnimeId = library.state.entries.associate { it.anime.id to it.category },
                onQueryChange = home.actions.onQueryChange,
                homeSearchState = home.state.search,
                onFilterApply = home.actions.onFilterApply,
                onSearchClear = home.actions.onSearchClear,
                onSearchLoadMore = home.actions.onSearchLoadMore,
                onSearchRetry = home.actions.onSearchRetry,
                onAnimeClick = navigation.actions.onAnimeClick,
                onRetry = catalog.actions.onRetry,
                onLoadMoreRetry = catalog.actions.onLoadMoreRetry,
                onSortSelected = catalog.actions.onSortSelected,
                onBrowseCatalog = navigation.actions.onBrowseCatalog,
                onOpenLibrary = navigation.actions.onOpenLibrary,
                baseHomeState = home.state.ui,
                onItemVisible = home.actions.onItemVisible,
                onHomeRefresh = home.actions.onRefresh,
                bottomContentPadding = topLevelBottomContentPadding,
            )
            AppDestination.CATALOG -> CatalogDestinationRoute(
                state = catalog.state.ui,
                listState = catalog.state.listState,
                libraryEntries = library.state.entries,
                query = catalog.state.query,
                onQueryChange = catalog.actions.onQueryChange,
                items = catalog.state.items,
                filters = catalog.state.filters,
                filterCatalog = catalog.state.filterCatalog,
                onFiltersChange = catalog.actions.onFiltersChange,
                onAnimeClick = navigation.actions.onAnimeClick,
                onRetry = catalog.actions.onRetry,
                onLoadMoreRetry = catalog.actions.onLoadMoreRetry,
                onSortSelected = catalog.actions.onSortSelected,
                bottomContentPadding = topLevelBottomContentPadding,
            )
            AppDestination.LIBRARY -> LibraryDestinationRoute(
                entries = library.state.entries,
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
            AppDestination.PROFILE -> ProfileDestinationRoute(
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
            )
            AppDestination.SOURCES -> SourcesDestinationRoute(
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
            )
            AppDestination.SETTINGS -> SettingsDestinationRoute(
                showExternalSources = content.currentRoute is AppRoute.ExternalSources,
                profileData = profile.state.data,
                languageMode = platform.dataContext.languageMode,
                onLanguageModeChange = settings.actions.onLanguageModeChange,
                darkTheme = settings.state.darkTheme,
                onThemeChange = settings.actions.onThemeChange,
                themeMode = settings.state.themeMode,
                onThemeModeChange = settings.actions.onThemeModeChange,
                appVersionName = settings.state.appVersionName,
                useSystemColorScheme = settings.state.useSystemColorScheme,
                useAmoledTheme = settings.state.useAmoledTheme,
                autoSkipSegments = settings.state.autoSkipSegments,
                onSystemColorSchemeChange = settings.actions.onSystemColorSchemeChange,
                onAmoledChange = settings.actions.onAmoledChange,
                onAutoSkipChange = settings.actions.onAutoSkipChange,
                onConfigureNotifications = settings.actions.onConfigureNotifications,
                notificationPermissionState = settings.state.notificationPermissionState,
                showSettingsBackButton = settings.state.showBackButton,
                onSettingsBack = navigation.actions.onSettingsBack,
                onGitHubClick = platform.hostContext.onGitHubClick,
                discordEnabled = settings.state.discordEnabled,
                discordAvailable = settings.state.discordAvailable,
                onDiscordClick = settings.actions.onDiscordClick,
                onDiscordChange = settings.actions.onDiscordChange,
                onCheckForUpdates = settings.actions.onCheckForUpdates,
                onExportLogs = settings.actions.onExportLogs,
                notificationsAvailable = settings.state.notificationsAvailable,
                externalSourceRepositoryState = sources.externalSourcesState.repository,
                externalSourceRepositoryController = sources.externalSourcesState.controller,
                settingsListState = settings.listsState.settings,
                externalSourcesListState = settings.listsState.externalSources,
                bottomContentPadding = topLevelBottomContentPadding,
                onExternalSourcesClick = navigation.actions.onExternalSourcesClick,
            )
        }
    }
}
