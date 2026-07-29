package org.akkirrai.hibiki.shared.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppTonalSurface
import org.akkirrai.hibiki.shared.design.component.AppBottomBarContentExtraPadding
import org.akkirrai.hibiki.shared.design.component.AppBottomBarHeight
import org.akkirrai.hibiki.shared.app.AppProductionRoot
import org.akkirrai.hibiki.shared.design.component.SectionHeader
import org.akkirrai.hibiki.shared.design.component.AppPosterAnimeCard
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogUiState
import org.akkirrai.hibiki.shared.catalog.AppCatalogScreen
import org.akkirrai.hibiki.shared.catalog.AppCatalogScreenLabels
import org.akkirrai.hibiki.shared.catalog.CatalogSort
import org.akkirrai.hibiki.shared.catalog.toAlias
import org.akkirrai.hibiki.shared.catalog.PrototypeAnimeCatalogRepository
import org.akkirrai.hibiki.shared.details.AppDetailsScreen
import org.akkirrai.hibiki.shared.design.HibikiDarkColorScheme
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.library.LibraryEntry
import org.akkirrai.hibiki.shared.library.LibraryPresenter
import org.akkirrai.hibiki.shared.library.LibraryUiState
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.AppLibraryEntriesContent
import org.akkirrai.hibiki.shared.library.AppLibraryHeader
import org.akkirrai.hibiki.shared.library.AppLibrarySearchBar
import org.akkirrai.hibiki.shared.library.AppLibraryEmptyState
import org.akkirrai.hibiki.shared.library.AppLibraryEntryCard
import org.akkirrai.hibiki.shared.library.buildLibraryFilterCatalog
import org.akkirrai.hibiki.shared.library.isRussianLibraryLanguage
import org.akkirrai.hibiki.shared.library.toAnimeSearchFilters
import org.akkirrai.hibiki.shared.library.toLibrarySearchFilters
import org.akkirrai.hibiki.shared.library.resolveLibraryEmptyStateText
import org.akkirrai.hibiki.shared.library.icon
import org.akkirrai.hibiki.shared.catalog.AppCatalogFilterSheet
import org.akkirrai.hibiki.shared.catalog.defaultCatalogFilterYearRange
import org.akkirrai.hibiki.shared.home.AppHomeScreen
import org.akkirrai.hibiki.shared.home.AppHomeScreenLabels
import org.akkirrai.hibiki.shared.home.HomeUiState
import org.akkirrai.hibiki.shared.home.HomeDataRepository
import org.akkirrai.hibiki.shared.home.HomePresenter
import org.akkirrai.hibiki.shared.model.SearchUiState
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.LocalProfilePresenter
import org.akkirrai.hibiki.shared.profile.LocalProfileSummary
import org.akkirrai.hibiki.shared.profile.AppLocalProfileLabels
import org.akkirrai.hibiki.shared.profile.AppLocalProfileScreen
import org.akkirrai.hibiki.shared.profile.ProfileAvatarImage
import org.akkirrai.hibiki.shared.profile.ProfileAvatarPlaceholder
import org.akkirrai.hibiki.shared.profile.LocalProfileSnapshotLabels
import org.akkirrai.hibiki.shared.profile.buildLocalProfileSnapshot
import org.akkirrai.hibiki.shared.profile.formatDurationHours
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.AppSettingsState
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.settings.InMemoryAppSettingsStore
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.settings.AppSettingsCard
import org.akkirrai.hibiki.shared.settings.AppSettingsCardLabels
import org.akkirrai.hibiki.shared.settings.AppSettingsScreen
import org.akkirrai.hibiki.shared.settings.AppSettingsScreenLabels
import org.akkirrai.hibiki.shared.text.DefaultAppTextResolver
import org.akkirrai.hibiki.shared.text.LocalAppTextResolver
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination
import org.akkirrai.hibiki.shared.search.AppSearchField
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.AppLocalSourcesScreen
import org.akkirrai.hibiki.shared.onboarding.AppOnboardingScreen

@Composable
fun HibikiAppShell(
    modifier: Modifier = Modifier,
    repository: AnimeCatalogRepository = PrototypeAnimeCatalogRepository,
    homeRepository: HomeDataRepository? = null,
    libraryRepository: LibraryRepository,
    profileRepository: LocalProfileDataRepository,
    settingsStore: AppSettingsStore = InMemoryAppSettingsStore(),
    systemLanguage: String = "en",
    appVersionName: String = "dev",
    enableOnboarding: Boolean = false,
    onboardingNotificationPermissionState: NotificationPermissionState = NotificationPermissionState.NOT_ASKED,
    onProfileAvatarEdit: (((String) -> Unit) -> Unit) = {},
    sources: List<AppSourceDescriptor> = emptyList(),
    selectedSourceId: String? = null,
    onSourceSelected: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val presenter = remember(repository) { AnimeCatalogPresenter(repository, scope) }
    val state by presenter.state.collectAsState()
    val homePresenter = remember(homeRepository) { HomePresenter() }
    val homeState by homePresenter.state.collectAsState()
    val catalogListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val sourceSearchPresenter = remember(repository) { AnimeCatalogPresenter(repository, scope, 12) }
    val sourceSearchState by sourceSearchPresenter.state.collectAsState()
    val libraryPresenter = remember(libraryRepository) { LibraryPresenter() }
    val libraryState by libraryPresenter.state.collectAsState()
    val profilePresenter = remember(profileRepository) { LocalProfilePresenter() }
    val profileState by profilePresenter.state.collectAsState()
    var selectedTab by remember { mutableStateOf(AppDestination.HOME) }
    val initialSettings = remember(settingsStore) { settingsStore.load() }
    var languageMode by remember(settingsStore) { mutableStateOf(initialSettings.languageMode) }
    var darkTheme by remember(settingsStore) { mutableStateOf(initialSettings.darkTheme) }
    var useSystemColorScheme by remember(settingsStore) { mutableStateOf(initialSettings.useSystemColorScheme) }
    var useAmoledTheme by remember(settingsStore) { mutableStateOf(initialSettings.useAmoledTheme) }
    var autoSkipSegments by remember(settingsStore) { mutableStateOf(initialSettings.autoSkipSegments) }
    var onboardingCompleted by remember(settingsStore) { mutableStateOf(initialSettings.onboardingCompleted) }
    var onboardingSourceId by remember(settingsStore) {
        mutableStateOf(initialSettings.selectedSourceId ?: selectedSourceId)
    }
    var isEditingProfile by remember { mutableStateOf(false) }
    var editedProfileName by remember(profileState.data.profileName) { mutableStateOf(profileState.data.profileName) }

    DisposableEffect(presenter) {
        presenter.loadFilterCatalog()
        presenter.search()
        onDispose {
            presenter.close()
            sourceSearchPresenter.close()
        }
    }

    LaunchedEffect(libraryRepository, state.selectedAnime) {
        libraryPresenter.updateEntries(libraryRepository.getEntries())
    }

    LaunchedEffect(homeRepository) {
        if (homeRepository == null) {
            homePresenter.setState(HomeUiState())
        } else {
            homePresenter.setState(homeRepository.fallbackHomeState())
            homePresenter.setState(homeRepository.loadHomeState())
        }
    }

    LaunchedEffect(profileRepository) {
        profilePresenter.load(profileRepository)
    }

    val refreshLocalData = {
        scope.launch {
            libraryPresenter.updateEntries(libraryRepository.getEntries())
            profilePresenter.load(profileRepository)
            homeRepository?.let { repository ->
                homePresenter.setState(repository.loadHomeState())
            }
        }
        Unit
    }

    CompositionLocalProvider(
        LocalAppTextResolver provides DefaultAppTextResolver(languageMode, systemLanguage),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) HibikiDarkColorScheme else HibikiLightColorScheme,
            typography = HibikiTypography,
        ) {
            Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box {
                    fun saveSettings() {
                        settingsStore.save(
                            AppSettingsState(
                                languageMode = languageMode,
                                darkTheme = darkTheme,
                                useSystemColorScheme = useSystemColorScheme,
                                useAmoledTheme = useAmoledTheme,
                                autoSkipSegments = autoSkipSegments,
                                onboardingCompleted = onboardingCompleted,
                                selectedSourceId = onboardingSourceId,
                            ),
                        )
                    }
                    val onLanguageModeChange = { mode: LanguageMode ->
                        languageMode = mode
                        saveSettings()
                    }
                    val onThemeChange = { dark: Boolean ->
                        darkTheme = dark
                        saveSettings()
                    }
                    val onSystemColorSchemeChange = { enabled: Boolean ->
                        useSystemColorScheme = enabled
                        saveSettings()
                    }
                    val onAmoledChange = { enabled: Boolean ->
                        useAmoledTheme = enabled
                        saveSettings()
                    }
                    val onAutoSkipChange = { enabled: Boolean ->
                        autoSkipSegments = enabled
                        saveSettings()
                    }
                    val topLevelDestination = when (selectedTab) {
                        AppDestination.HOME -> AppTopLevelDestination.HOME
                        AppDestination.CATALOG -> AppTopLevelDestination.CATALOG
                        AppDestination.LIBRARY -> AppTopLevelDestination.LIBRARY
                        AppDestination.SOURCES -> AppTopLevelDestination.SOURCES
                        AppDestination.PROFILE, AppDestination.SETTINGS -> AppTopLevelDestination.PROFILE
                    }
                    AppProductionRoot(
                        currentDestination = topLevelDestination,
                        onNavigationEvent = { event ->
                            if (event is AppNavigationEvent.SelectTopLevel) {
                                selectedTab = when (event.destination) {
                                    AppTopLevelDestination.HOME -> AppDestination.HOME
                                    AppTopLevelDestination.CATALOG -> AppDestination.CATALOG
                                    AppTopLevelDestination.LIBRARY -> AppDestination.LIBRARY
                                    AppTopLevelDestination.SOURCES -> AppDestination.SOURCES
                                    AppTopLevelDestination.PROFILE -> AppDestination.PROFILE
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        AppDestinationContent(
                            selectedTab = selectedTab,
                            systemLanguage = systemLanguage,
                            appVersionName = appVersionName,
                            catalogState = state,
                            homeState = homeState,
                            onHomeRefresh = {
                                homeRepository?.let { repo ->
                                    scope.launch {
                                        homePresenter.setState(homePresenter.state.value.copy(isLoading = true))
                                        homePresenter.setState(repo.refreshHomeState())
                                    }
                                }
                            },
                            catalogListState = catalogListState,
                            query = state.query,
                            onQueryChange = presenter::onQueryChange,
                            items = state.items,
                            filters = state.filters,
                            filterCatalog = state.filterCatalog,
                            onFiltersChange = presenter::updateFilters,
                            onCatalogRetry = presenter::search,
                            onCatalogLoadMoreRetry = presenter::loadMore,
                            onCatalogSortSelected = { sort ->
                                presenter.setFilters(state.filters.copy(sortAlias = sort.toAlias()))
                                presenter.search()
                            },
                            libraryState = libraryState,
                            onLibraryCategorySelected = libraryPresenter::selectCategory,
                            onLibrarySearchQueryChange = libraryPresenter::onSearchQueryChange,
                            onLibrarySearchClear = libraryPresenter::clearSearch,
                            onLibraryFiltersApply = libraryPresenter::applySearchFilters,
                            onBrowseCatalog = { selectedTab = AppDestination.CATALOG },
                            onOpenLibrary = { selectedTab = AppDestination.LIBRARY },
                            selectedAnime = state.selectedAnime,
                            onAnimeClick = presenter::openDetails,
                            onBackFromDetails = presenter::closeDetails,
                            isDetailsLoading = state.isDetailsLoading,
                            detailsError = state.detailsError,
                            libraryRepository = libraryRepository,
                            languageMode = languageMode,
                            onLanguageModeChange = onLanguageModeChange,
                            darkTheme = darkTheme,
                            onThemeChange = onThemeChange,
                            useSystemColorScheme = useSystemColorScheme,
                            useAmoledTheme = useAmoledTheme,
                            autoSkipSegments = autoSkipSegments,
                            onSystemColorSchemeChange = onSystemColorSchemeChange,
                            onAmoledChange = onAmoledChange,
                            onAutoSkipChange = onAutoSkipChange,
                            onLibraryChanged = refreshLocalData,
                            libraryEntries = libraryState.visibleEntries,
                            profileData = profileState.data,
                            isEditingProfile = isEditingProfile,
                            editedProfileName = editedProfileName,
                            onProfileNameChange = { editedProfileName = it },
                            onProfileEditClick = { isEditingProfile = !isEditingProfile },
                            onProfileSaveClick = {
                                profileRepository.updateProfileName(editedProfileName)
                                profilePresenter.updateProfileName(editedProfileName)
                                isEditingProfile = false
                            },
                            onProfileSettingsClick = { selectedTab = AppDestination.SETTINGS },
                            onProfileAvatarEdit = onProfileAvatarEdit,
                            onProfileAvatarPicked = { uri ->
                                profileRepository.updateProfileAvatar(uri)
                                profilePresenter.updateProfileAvatar(uri)
                            },
                            profileRepository = profileRepository,
                            sources = sources,
                            selectedSourceId = selectedSourceId,
                            onSourceSelected = { sourceId ->
                                repository.selectSource(sourceId)
                                presenter.clear()
                                presenter.loadFilterCatalog()
                                presenter.search()
                                sourceSearchPresenter.clear()
                                onSourceSelected(sourceId)
                            },
                            sourceSearchState = sourceSearchState,
                            onSourceSearchQueryChange = sourceSearchPresenter::onQueryChange,
                            onSourceSearchClear = sourceSearchPresenter::clear,
                            onSourceSearchRetry = sourceSearchPresenter::search,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    bottom = if (state.selectedAnime == null) {
                                        AppBottomBarHeight + AppBottomBarContentExtraPadding
                                    } else {
                                        0.dp
                                    },
                                ),
                        )
                    }
                    if (enableOnboarding && !onboardingCompleted) {
                        AppOnboardingScreen(
                            sources = sources,
                            initialSourceId = onboardingSourceId,
                            notificationPermissionState = onboardingNotificationPermissionState,
                            onRequestNotificationPermission = {},
                            onComplete = { sourceId ->
                                onboardingSourceId = sourceId
                                onboardingCompleted = true
                                settingsStore.save(
                                    initialSettings.copy(
                                        onboardingCompleted = true,
                                        selectedSourceId = sourceId,
                                    ),
                                )
                                repository.selectSource(sourceId)
                                onSourceSelected(sourceId)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WideAppLayout(
    selectedTab: AppDestination,
    onTabSelected: (AppDestination) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    selectedAnime: Anime?,
    onAnimeClick: (Anime) -> Unit,
    onBackFromDetails: () -> Unit,
    isDetailsLoading: Boolean,
    detailsError: String?,
    libraryRepository: LibraryRepository,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    libraryEntries: List<LibraryEntry>,
    profileData: LocalProfileData,
    isEditingProfile: Boolean,
    editedProfileName: String,
    onProfileNameChange: (String) -> Unit,
    onProfileEditClick: () -> Unit,
    onProfileSaveClick: () -> Unit,
    onProfileSettingsClick: () -> Unit,
    onProfileAvatarEdit: (((String) -> Unit) -> Unit),
    onProfileAvatarPicked: (String) -> Unit,
    profileRepository: LocalProfileDataRepository,
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    onSourceSelected: (String) -> Unit,
    sourceSearchState: AnimeCatalogUiState,
    onSourceSearchQueryChange: (String) -> Unit,
    onSourceSearchClear: () -> Unit,
    onSourceSearchRetry: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        AppSidebar(selectedTab, onTabSelected)
        HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
        AppDestinationContent(
            selectedTab,
            query,
            onQueryChange,
            items,
            filters,
            filterCatalog,
            onFiltersChange,
            selectedAnime,
            onAnimeClick,
            onBackFromDetails,
            isDetailsLoading,
            detailsError,
            libraryRepository,
            languageMode,
            onLanguageModeChange,
            darkTheme,
            onThemeChange,
            libraryEntries,
            profileData,
            isEditingProfile,
            editedProfileName,
            onProfileNameChange,
            onProfileEditClick,
            onProfileSaveClick,
            onProfileSettingsClick,
            onProfileAvatarEdit,
            onProfileAvatarPicked,
            profileRepository,
            sources,
            selectedSourceId,
            onSourceSelected,
            sourceSearchState,
            onSourceSearchQueryChange,
            onSourceSearchClear,
            onSourceSearchRetry,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactAppLayout(
    selectedTab: AppDestination,
    onTabSelected: (AppDestination) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    selectedAnime: Anime?,
    onAnimeClick: (Anime) -> Unit,
    onBackFromDetails: () -> Unit,
    isDetailsLoading: Boolean,
    detailsError: String?,
    libraryRepository: LibraryRepository,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    libraryEntries: List<LibraryEntry>,
    profileData: LocalProfileData,
    isEditingProfile: Boolean,
    editedProfileName: String,
    onProfileNameChange: (String) -> Unit,
    onProfileEditClick: () -> Unit,
    onProfileSaveClick: () -> Unit,
    onProfileSettingsClick: () -> Unit,
    onProfileAvatarEdit: (((String) -> Unit) -> Unit),
    onProfileAvatarPicked: (String) -> Unit,
    profileRepository: LocalProfileDataRepository,
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    onSourceSelected: (String) -> Unit,
    sourceSearchState: AnimeCatalogUiState,
    onSourceSearchQueryChange: (String) -> Unit,
    onSourceSearchClear: () -> Unit,
    onSourceSearchRetry: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            if (selectedAnime == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AppDestination.entries.forEach { tab ->
                        FilterChip(
                            selected = tab == selectedTab,
                            onClick = { onTabSelected(tab) },
                            label = { Text(appText(tab.textKey)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        AppDestinationContent(
            selectedTab,
            query,
            onQueryChange,
            items,
            filters,
            filterCatalog,
            onFiltersChange,
            selectedAnime,
            onAnimeClick,
            onBackFromDetails,
            isDetailsLoading,
            detailsError,
            libraryRepository,
            languageMode,
            onLanguageModeChange,
            darkTheme,
            onThemeChange,
            libraryEntries,
            profileData,
            isEditingProfile,
            editedProfileName,
            onProfileNameChange,
            onProfileEditClick,
            onProfileSaveClick,
            onProfileSettingsClick,
            onProfileAvatarEdit,
            onProfileAvatarPicked,
            profileRepository,
            sources,
            selectedSourceId,
            onSourceSelected,
            sourceSearchState,
            onSourceSearchQueryChange,
            onSourceSearchClear,
            onSourceSearchRetry,
            Modifier.padding(padding),
        )
    }
}

@Composable
private fun AppSidebar(selectedTab: AppDestination, onTabSelected: (AppDestination) -> Unit) {
    Column(
        modifier = Modifier.width(220.dp).fillMaxHeight().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = appText(AppTextKey.AppName),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = appText(AppTextKey.PrototypeNotice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        AppDestination.entries.forEach { tab ->
            NavigationItem(tab, selectedTab == tab, { onTabSelected(tab) })
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = appText(AppTextKey.DesktopPreview),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NavigationItem(tab: AppDestination, selected: Boolean, onClick: () -> Unit) {
    AppTonalSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(UiDimens.MediumCorner),
    ) {
        Text(
            text = appText(tab.textKey),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AppDestinationContent(
    selectedTab: AppDestination,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    selectedAnime: Anime?,
    onAnimeClick: (Anime) -> Unit,
    onBackFromDetails: () -> Unit,
    isDetailsLoading: Boolean,
    detailsError: String?,
    libraryRepository: LibraryRepository,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    libraryEntries: List<LibraryEntry>,
    profileData: LocalProfileData,
    isEditingProfile: Boolean,
    editedProfileName: String,
    onProfileNameChange: (String) -> Unit,
    onProfileEditClick: () -> Unit,
    onProfileSaveClick: () -> Unit,
    onProfileSettingsClick: () -> Unit,
    onProfileAvatarEdit: (((String) -> Unit) -> Unit),
    onProfileAvatarPicked: (String) -> Unit,
    profileRepository: LocalProfileDataRepository,
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    onSourceSelected: (String) -> Unit,
    sourceSearchState: AnimeCatalogUiState,
    onSourceSearchQueryChange: (String) -> Unit,
    onSourceSearchClear: () -> Unit,
    onSourceSearchRetry: () -> Unit,
    modifier: Modifier = Modifier,
    catalogState: AnimeCatalogUiState = AnimeCatalogUiState(),
    catalogListState: LazyListState = LazyListState(),
    onCatalogRetry: () -> Unit = {},
    onCatalogLoadMoreRetry: () -> Unit = {},
    onCatalogSortSelected: (CatalogSort) -> Unit = {},
    libraryState: LibraryUiState = LibraryUiState(),
    onLibraryCategorySelected: (LibraryCategory) -> Unit = {},
    onLibrarySearchQueryChange: (String) -> Unit = {},
    onLibrarySearchClear: () -> Unit = {},
    onLibraryFiltersApply: (org.akkirrai.hibiki.shared.library.LibrarySearchFilters) -> Unit = {},
    systemLanguage: String = "en",
    appVersionName: String = "dev",
    onBrowseCatalog: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    homeState: HomeUiState = HomeUiState(),
    onHomeRefresh: () -> Unit = {},
    useSystemColorScheme: Boolean = true,
    useAmoledTheme: Boolean = false,
    autoSkipSegments: Boolean = false,
    onSystemColorSchemeChange: (Boolean) -> Unit = {},
    onAmoledChange: (Boolean) -> Unit = {},
    onAutoSkipChange: (Boolean) -> Unit = {},
    onLibraryChanged: () -> Unit = {},
) {
    if (selectedAnime != null) {
        AppDetailsScreen(
            anime = selectedAnime,
            onBackClick = onBackFromDetails,
            onRelatedAnimeClick = onAnimeClick,
            canWatch = false,
            onWatchClick = {},
            libraryRepository = libraryRepository,
            onLibraryCategoryChange = { onLibraryChanged() },
            modifier = modifier.fillMaxSize(),
            isDetailsLoading = isDetailsLoading,
            detailsError = detailsError,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(appText(selectedTab.textKey), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = if (selectedTab == AppDestination.SETTINGS) {
                        appText(AppTextKey.SettingsSubtitle)
                    } else {
                        appText(AppTextKey.PrototypeSubtitle)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selectedTab != AppDestination.SETTINGS && selectedTab != AppDestination.PROFILE) {
                Button(onClick = { }) { Text(appText(AppTextKey.ExploreCatalog)) }
            }
        }
        when (selectedTab) {
                AppDestination.HOME -> HomeScreen(
                    state = catalogState,
                    listState = catalogListState,
                    libraryStatusByAnimeId = libraryEntries.associate { it.anime.id to it.category },
                    libraryEntries = libraryEntries,
                    query = query,
                    onQueryChange = onQueryChange,
                    items = items,
                    filters = filters,
                    filterCatalog = filterCatalog,
                    onFiltersChange = onFiltersChange,
                    onAnimeClick = onAnimeClick,
                    onRetry = onCatalogRetry,
                    onLoadMoreRetry = onCatalogLoadMoreRetry,
                    onSortSelected = onCatalogSortSelected,
                    onBrowseCatalog = onBrowseCatalog,
                    onOpenLibrary = onOpenLibrary,
                    baseHomeState = homeState,
                    onHomeRefresh = onHomeRefresh,
                )
                AppDestination.CATALOG -> SearchScreen(
                    state = catalogState,
                    listState = catalogListState,
                    libraryStatusByAnimeId = libraryEntries.associate { it.anime.id to it.category },
                    query = query,
                    onQueryChange = onQueryChange,
                    items = items,
                    filters = filters,
                    filterCatalog = filterCatalog,
                    onFiltersChange = onFiltersChange,
                    onAnimeClick = onAnimeClick,
                    onRetry = onCatalogRetry,
                    onLoadMoreRetry = onCatalogLoadMoreRetry,
                    onSortSelected = onCatalogSortSelected,
                )
                AppDestination.LIBRARY -> LibraryScreen(
                    entries = libraryEntries,
                    state = libraryState,
                    onAnimeClick = onAnimeClick,
                    onCategorySelected = onLibraryCategorySelected,
                    onSearchQueryChange = onLibrarySearchQueryChange,
                    onSearchClear = onLibrarySearchClear,
                    onFiltersApply = onLibraryFiltersApply,
                    languageMode = languageMode,
                    systemLanguage = systemLanguage,
                )
                AppDestination.PROFILE -> {
                    val categoryLabels = mapOf(
                        LibraryCategory.Watching to appText(AppTextKey.LibraryWatching),
                        LibraryCategory.Planned to appText(AppTextKey.LibraryPlanned),
                        LibraryCategory.Completed to appText(AppTextKey.LibraryCompleted),
                        LibraryCategory.Dropped to appText(AppTextKey.LibraryDropped),
                        LibraryCategory.OnHold to appText(AppTextKey.LibraryOnHold),
                        LibraryCategory.Favorite to appText(AppTextKey.LibraryFavorite),
                        LibraryCategory.Saved to appText(AppTextKey.LibrarySaved),
                    )
                    val snapshot = buildLocalProfileSnapshot(
                        data = profileData,
                        activityDateStrings = profileData.activity.map { it.date }.distinct(),
                        labels = LocalProfileSnapshotLabels(
                            durationLabel = { duration -> "${formatDurationHours(duration)} h" },
                            categoryLabel = { category -> categoryLabels.getValue(category) },
                            dateLabel = { it.toString() },
                            activityDateLabel = { it },
                        ),
                    )
                    AppLocalProfileScreen(
                        snapshot = snapshot,
                        profileName = profileData.profileName.ifBlank { appText(AppTextKey.AppName) },
                        isEditing = isEditingProfile,
                        editedName = editedProfileName,
                        bottomContentPadding = 24.dp,
                        labels = AppLocalProfileLabels(
                            overviewTab = appText(AppTextKey.ProfileTabOverview),
                            activityTab = appText(AppTextKey.ProfileTabActivity),
                            favoritesTab = appText(AppTextKey.ProfileTabFavorites),
                            profileNameLabel = appText(AppTextKey.ProfileName),
                            editContentDescription = appText(AppTextKey.ProfileEdit),
                            saveContentDescription = appText(AppTextKey.ProfileSave),
                            changeAvatarContentDescription = appText(AppTextKey.ProfileChangeAvatar),
                            settingsContentDescription = appText(AppTextKey.Settings),
                            totalLabel = appText(AppTextKey.ProfileStatTotal),
                            daysLabel = appText(AppTextKey.ProfileStatDays),
                            timeLabel = appText(AppTextKey.ProfileStatTime),
                            recentTitle = appText(AppTextKey.ProfileRecent),
                            recentEmptyText = appText(AppTextKey.ProfileEmptyRecent),
                            favoritesEmptyText = appText(AppTextKey.ProfileEmptyFavorites),
                            analyticsWatchTitle = appText(AppTextKey.ProfileAnalyticsWatchTime),
                            analyticsTotalLabel = appText(AppTextKey.ProfileAnalyticsTotal),
                            analyticsGenresTitle = appText(AppTextKey.ProfileAnalyticsGenres),
                            analyticsGenresLabel = appText(AppTextKey.ProfileAnalyticsGenresLabel),
                            analyticsTitle = appText(AppTextKey.Profile),
                            episodesStatLabel = appText(AppTextKey.ProfileEpisodes),
                            watchStatLabel = appText(AppTextKey.ProfileAnalyticsWatched),
                            activityTitle = appText(AppTextKey.ProfileActivity),
                        ),
                        onNameChange = onProfileNameChange,
                        onAvatarEditClick = {
                            onProfileAvatarEdit(onProfileAvatarPicked)
                        },
                        onEditActionClick = if (isEditingProfile) onProfileSaveClick else onProfileEditClick,
                        onSettingsClick = onProfileSettingsClick,
                        avatarContent = { avatarModifier ->
                            profileData.profileAvatarUri?.let { ProfileAvatarImage(it) }
                                ?: ProfileAvatarPlaceholder(avatarModifier)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                AppDestination.SOURCES -> AppLocalSourcesScreen(
                    sources = sources,
                    selectedSourceId = selectedSourceId,
                    bottomContentPadding = 24.dp,
                    emptyText = appText(AppTextKey.Sources),
                    languageLabel = { language ->
                        when (language.lowercase()) {
                            "ru", "русский" -> appText(AppTextKey.LanguageRussian)
                            "en", "english" -> appText(AppTextKey.LanguageEnglish)
                            else -> language.uppercase()
                        }
                    },
                    onSourceSelected = onSourceSelected,
                    searchQuery = sourceSearchState.query,
                    searchItems = sourceSearchState.items,
                    isSearchLoading = sourceSearchState.isLoading,
                    searchError = sourceSearchState.error != null,
                    searchSourceId = sources.firstOrNull(AppSourceDescriptor::supportsSearch)?.id.orEmpty(),
                    searchSourceName = sources.firstOrNull(AppSourceDescriptor::supportsSearch)?.name.orEmpty(),
                    onSearchQueryChange = onSourceSearchQueryChange,
                    onSearchClear = onSourceSearchClear,
                    searchPlaceholder = appText(AppTextKey.SearchPlaceholder),
                    searchErrorLabel = sourceSearchState.error ?: appText(AppTextKey.Unknown),
                    searchRetryLabel = appText(AppTextKey.Search),
                    searchEmptyTitle = appText(AppTextKey.Sources),
                    announcementLabel = appText(AppTextKey.Announcement),
                    movieLabel = appText(AppTextKey.Movie),
                    onSearchRetry = onSourceSearchRetry,
                    onAnimeClick = onAnimeClick,
                    modifier = Modifier.fillMaxSize(),
                )
                AppDestination.SETTINGS -> SettingsScreen(
                    profileData = profileData,
                    languageMode = languageMode,
                    onLanguageModeChange = onLanguageModeChange,
                    darkTheme = darkTheme,
                    onThemeChange = onThemeChange,
                    versionName = appVersionName,
                    useSystemColorScheme = useSystemColorScheme,
                    useAmoledTheme = useAmoledTheme,
                    autoSkipSegments = autoSkipSegments,
                    onSystemColorSchemeChange = onSystemColorSchemeChange,
                    onAmoledChange = onAmoledChange,
                    onAutoSkipChange = onAutoSkipChange,
                )
        }
    }
}

private fun LibraryCategory.profileTextKey(): AppTextKey = when (this) {
    LibraryCategory.Watching -> AppTextKey.LibraryWatching
    LibraryCategory.Planned -> AppTextKey.LibraryPlanned
    LibraryCategory.Completed -> AppTextKey.LibraryCompleted
    LibraryCategory.Dropped -> AppTextKey.LibraryDropped
    LibraryCategory.OnHold -> AppTextKey.LibraryOnHold
    LibraryCategory.Favorite -> AppTextKey.LibraryFavorite
    LibraryCategory.Saved -> AppTextKey.LibrarySaved
}

@Composable
private fun LibraryCategory.libraryText(): String = when (this) {
    LibraryCategory.Watching -> appText(AppTextKey.LibraryWatching)
    LibraryCategory.Planned -> appText(AppTextKey.LibraryPlanned)
    LibraryCategory.Completed -> appText(AppTextKey.LibraryCompleted)
    LibraryCategory.Dropped -> appText(AppTextKey.LibraryDropped)
    LibraryCategory.OnHold -> appText(AppTextKey.LibraryOnHold)
    LibraryCategory.Favorite -> appText(AppTextKey.LibraryFavorite)
    LibraryCategory.Saved -> appText(AppTextKey.LibrarySaved)
}

@Composable
private fun defaultCatalogScreenLabels(): AppCatalogScreenLabels {
    val categoryLabels = mapOf(
        LibraryCategory.Watching to appText(AppTextKey.LibraryWatching),
        LibraryCategory.Planned to appText(AppTextKey.LibraryPlanned),
        LibraryCategory.Completed to appText(AppTextKey.LibraryCompleted),
        LibraryCategory.Dropped to appText(AppTextKey.LibraryDropped),
        LibraryCategory.OnHold to appText(AppTextKey.LibraryOnHold),
        LibraryCategory.Favorite to appText(AppTextKey.LibraryFavorite),
        LibraryCategory.Saved to appText(AppTextKey.LibrarySaved),
    )
    return AppCatalogScreenLabels(
        errorTitle = appText(AppTextKey.CatalogError),
        retryLabel = appText(AppTextKey.SearchRetry),
        announcementLabel = appText(AppTextKey.Announcement),
        movieLabel = appText(AppTextKey.Type),
        searchPlaceholder = appText(AppTextKey.SearchPlaceholder),
        filterContentDescription = appText(AppTextKey.SearchFilters),
        clearContentDescription = appText(AppTextKey.Back),
        sortTitle = appText(AppTextKey.CatalogSortTitle),
        sortLabels = mapOf(
            CatalogSort.Alphabetical to appText(AppTextKey.CatalogSortAlphabetical),
            CatalogSort.Popular to appText(AppTextKey.CatalogSortPopular),
            CatalogSort.Updated to appText(AppTextKey.CatalogSortUpdated),
        ),
        filterUnavailable = appText(AppTextKey.FilterUnavailable),
        typeTitle = appText(AppTextKey.Type),
        genresTitle = appText(AppTextKey.Genres),
        yearTitle = appText(AppTextKey.ReleaseDate),
        yearAllLabel = appText(AppTextKey.FilterAllYears),
        yearFromLabel = appText(AppTextKey.FilterFromYear),
        yearToLabel = appText(AppTextKey.FilterToYear),
        statusTitle = appText(AppTextKey.Status),
        resetLabel = appText(AppTextKey.FilterReset),
        applyLabel = appText(AppTextKey.FilterApply),
        libraryStatusLabel = { category -> categoryLabels.getValue(category) },
        optionText = { it.title },
    )
}

@Composable
private fun defaultHomeScreenLabels(): AppHomeScreenLabels {
    val categoryLabels = LibraryCategory.entries.associateWith { it.libraryText() }
    return AppHomeScreenLabels(
        searchPlaceholder = appText(AppTextKey.SearchPlaceholder),
        searchFilters = appText(AppTextKey.SearchFilters),
        searchClear = appText(AppTextKey.Back),
        searchLoadMore = appText(AppTextKey.HomeSearchLoadMore),
        searchEmptyTitle = appText(AppTextKey.HomeSearchEmptyTitle),
        searchEmptyMessage = appText(AppTextKey.HomeSearchEmptyBody),
        resultsCountLabel = { count -> count.toString() },
        continueTitle = appText(AppTextKey.HomeContinueTitle),
        continueEmptyTitle = appText(AppTextKey.HomeContinueEmptyTitle),
        continueEmptyMessage = appText(AppTextKey.HomeContinueEmptyBody),
        continueOpenHint = appText(AppTextKey.HomeContinueOpenHint),
        recentlyWatchedTitle = appText(AppTextKey.HomeRecentlyWatched),
        recentlyAddedTitle = appText(AppTextKey.HomeRecentlyAdded),
        announcementLabel = appText(AppTextKey.Announcement),
        movieLabel = appText(AppTextKey.Type),
        personalEmptyTitle = appText(AppTextKey.HomePersonalEmptyTitle),
        personalEmptyMessage = appText(AppTextKey.HomePersonalEmptyBody),
        personalEmptyActionLabel = appText(AppTextKey.HomeBrowseCatalog),
        filterUnavailable = appText(AppTextKey.FilterUnavailable),
        typeTitle = appText(AppTextKey.Type),
        genresTitle = appText(AppTextKey.Genres),
        yearTitle = appText(AppTextKey.ReleaseDate),
        yearAllLabel = appText(AppTextKey.FilterAllYears),
        yearFromLabel = appText(AppTextKey.FilterFromYear),
        yearToLabel = appText(AppTextKey.FilterToYear),
        statusTitle = appText(AppTextKey.Status),
        resetLabel = appText(AppTextKey.FilterReset),
        applyLabel = appText(AppTextKey.FilterApply),
        libraryStatusLabel = { category -> categoryLabels.getValue(category) },
        optionText = { it.title },
    )
}

@Composable
private fun ColumnScope.HomeScreen(
    state: AnimeCatalogUiState,
    baseHomeState: HomeUiState,
    listState: LazyListState,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    libraryEntries: List<LibraryEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onRetry: () -> Unit,
    onLoadMoreRetry: () -> Unit,
    onSortSelected: (CatalogSort) -> Unit,
    onBrowseCatalog: () -> Unit,
    onOpenLibrary: () -> Unit,
    onHomeRefresh: () -> Unit,
) {
    val searchResult = when {
        query.isBlank() -> SearchUiState.Idle
        state.isLoading && items.isEmpty() -> SearchUiState.Loading
        items.isEmpty() -> SearchUiState.Empty
        else -> SearchUiState.Content(items = items, canLoadMore = false)
    }
    val homeState = baseHomeState.copy(
        recentlyAddedToLibrary = if (baseHomeState.recentlyAddedToLibrary.isEmpty()) {
            libraryEntries.filter { it.category != LibraryCategory.Saved }.map { it.anime }
        } else {
            baseHomeState.recentlyAddedToLibrary
        },
        searchQuery = query,
        searchResult = searchResult,
        searchFilterCatalog = filterCatalog,
        searchFilters = filters,
    )
    AppHomeScreen(
        state = homeState,
        listState = listState,
        bottomContentPadding = 0.dp,
        currentYear = 2026,
        libraryStatusByAnimeId = libraryStatusByAnimeId,
        labels = defaultHomeScreenLabels(),
        onQueryChange = onQueryChange,
        onClearSearch = { onQueryChange("") },
        onFilterApply = onFiltersChange,
        onRefresh = onHomeRefresh,
        onLoadMoreSearch = onLoadMoreRetry,
        onAnimeClick = onAnimeClick,
        onBrowseCatalog = onBrowseCatalog,
        onOpenLibrary = onOpenLibrary,
        onItemVisible = {},
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ColumnScope.SearchScreen(
    state: AnimeCatalogUiState,
    listState: LazyListState,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onRetry: () -> Unit,
    onLoadMoreRetry: () -> Unit,
    onSortSelected: (CatalogSort) -> Unit,
) {
    AppCatalogScreen(
        state = state,
        listState = listState,
        bottomContentPadding = 0.dp,
        currentYear = 2026,
        libraryStatusByAnimeId = libraryStatusByAnimeId,
        labels = defaultCatalogScreenLabels(),
        onQueryChange = onQueryChange,
        onRetry = onRetry,
        onLoadMoreRetry = onLoadMoreRetry,
        onItemVisible = {},
        onSortSelected = onSortSelected,
        onFiltersApply = onFiltersChange,
        onAnimeClick = onAnimeClick,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ColumnScope.LibraryScreen(
    entries: List<LibraryEntry>,
    state: LibraryUiState,
    onAnimeClick: (Anime) -> Unit,
    onCategorySelected: (LibraryCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onFiltersApply: (org.akkirrai.hibiki.shared.library.LibrarySearchFilters) -> Unit,
    languageMode: LanguageMode,
    systemLanguage: String,
) {
    var isFilterSheetOpen by remember { mutableStateOf(false) }
    val isRussian = isRussianLibraryLanguage(languageMode, systemLanguage)
    val categoryLabels = LibraryCategory.entries.associateWith { it.libraryText() }
    AppLibraryEntriesContent(
        state = state,
        modifier = Modifier.fillMaxSize(),
        bottomContentPadding = 24.dp,
        onEntryClick = { entry -> onAnimeClick(entry.anime) },
        headerContent = {
            AppLibraryHeader(
                searchContent = { searchModifier ->
                    AppLibrarySearchBar(
                        query = state.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClear = onSearchClear,
                        placeholder = appText(AppTextKey.SearchPlaceholder),
                        filterContentDescription = appText(AppTextKey.SearchFilters),
                        clearContentDescription = appText(AppTextKey.Back),
                        onFilterClick = { isFilterSheetOpen = true },
                        modifier = searchModifier,
                    )
                },
                selected = state.selectedCategory,
                categories = state.orderedCategories,
                counts = state.categoryCounts,
                label = { category -> categoryLabels.getValue(category) },
                icon = { category -> category.icon() },
                onSelected = onCategorySelected,
            )
        },
        emptyContent = { filtered ->
            val emptyState = resolveLibraryEmptyStateText(
                filtered = filtered,
                searchQuery = state.searchQuery,
                category = state.selectedCategory,
                emptyTitle = appText(AppTextKey.LibraryEmptyTitle),
                emptyMessage = appText(AppTextKey.LibraryEmptyBody),
                filteredTitle = appText(AppTextKey.LibraryFilteredEmptyTitle),
                searchTitle = appText(AppTextKey.LibrarySearchEmptyTitle),
                filteredMessage = appText(AppTextKey.LibraryFilteredEmptyBody),
                categoryLabels = categoryLabels,
            )
            AppLibraryEmptyState(title = emptyState.title, message = emptyState.message)
        },
        entryContent = { entry, entryModifier ->
            AppLibraryEntryCard(
                entry = entry,
                announcementLabel = appText(AppTextKey.Announcement),
                movieLabel = appText(AppTextKey.Type),
                onClick = { onAnimeClick(entry.anime) },
                libraryStatusLabel = { category -> categoryLabels.getValue(category) },
                modifier = entryModifier,
            )
        },
    )

    if (isFilterSheetOpen) {
        val catalog = buildLibraryFilterCatalog(
            typeOptions = state.filterCatalog.typeOptions,
            statusOptions = state.filterCatalog.statusOptions,
            genreOptions = state.filterCatalog.genreOptions,
            isRussian = isRussian,
        )
        AppCatalogFilterSheet(
            initialFilters = state.searchFilters.toAnimeSearchFilters(),
            filterCatalog = catalog,
            isFilterCatalogLoading = false,
            onApply = { filters ->
                onFiltersApply(filters.toLibrarySearchFilters(state.filterCatalog))
                isFilterSheetOpen = false
            },
            onDismissRequest = { isFilterSheetOpen = false },
            unavailableLabel = appText(AppTextKey.FilterUnavailable),
            typeTitle = appText(AppTextKey.Type),
            genresTitle = appText(AppTextKey.Genres),
            yearTitle = appText(AppTextKey.ReleaseDate),
            yearAllLabel = appText(AppTextKey.FilterAllYears),
            yearFromLabel = appText(AppTextKey.FilterFromYear),
            yearToLabel = appText(AppTextKey.FilterToYear),
            statusTitle = appText(AppTextKey.Status),
            resetLabel = appText(AppTextKey.FilterReset),
            applyLabel = appText(AppTextKey.FilterApply),
            defaultYearRange = defaultCatalogFilterYearRange(2026),
            optionText = { it.title },
            shape = RoundedCornerShape(UiDimens.LargeCorner),
            maxCollapsedGenreGroups = 3,
            maxCollapsedGenreItems = null,
        )
    }
}

@Composable
private fun SettingsScreen(
    profileData: LocalProfileData,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    versionName: String,
    useSystemColorScheme: Boolean,
    useAmoledTheme: Boolean,
    autoSkipSegments: Boolean,
    onSystemColorSchemeChange: (Boolean) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onAutoSkipChange: (Boolean) -> Unit,
) {
    AppSettingsScreen(
        languageMode = languageMode,
        darkTheme = darkTheme,
        useSystemColorScheme = useSystemColorScheme,
        useAmoledTheme = useAmoledTheme,
        autoSkipSegments = autoSkipSegments,
        labels = AppSettingsScreenLabels(
            appearance = appText(AppTextKey.SettingsAppearance),
            theme = appText(AppTextKey.SettingsTheme),
            themeSystem = appText(AppTextKey.ThemeSystem),
            themeLight = appText(AppTextKey.ThemeLight),
            themeDark = appText(AppTextKey.ThemeDark),
            systemColorScheme = appText(AppTextKey.SettingsSystemColorScheme),
            amoled = appText(AppTextKey.SettingsAmoled),
            preferences = appText(AppTextKey.SettingsPreferences),
            language = appText(AppTextKey.SettingsLanguage),
            languageSystem = appText(AppTextKey.LanguageSystem),
            languageRussian = appText(AppTextKey.LanguageRussian),
            languageEnglish = appText(AppTextKey.LanguageEnglish),
            notifications = appText(AppTextKey.SettingsNotifications),
            notificationsStatus = appText(AppTextKey.SettingsNotificationsStatus),
            player = appText(AppTextKey.SettingsPlayer),
            autoSkip = appText(AppTextKey.SettingsAutoSkip),
            experimental = appText(AppTextKey.SettingsExperimental),
            discord = appText(AppTextKey.SettingsDiscord),
            updates = appText(AppTextKey.SettingsUpdates),
            checkUpdates = appText(AppTextKey.SettingsCheckUpdates),
            support = appText(AppTextKey.SettingsSupport),
            exportLogs = appText(AppTextKey.SettingsExportLogs),
            appName = appText(AppTextKey.AppName),
            versionName = versionName,
        ),
        onLanguageModeChange = onLanguageModeChange,
        onThemeChange = onThemeChange,
        onSystemColorSchemeChange = onSystemColorSchemeChange,
        onAmoledChange = onAmoledChange,
        onAutoSkipChange = onAutoSkipChange,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ColumnScope.CatalogScreenContent(
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    onAnimeClick: (Anime) -> Unit,
    sectionTitle: String,
) {
    Spacer(Modifier.height(20.dp))
    AppSearchField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = {},
        modifier = Modifier.fillMaxWidth(),
        placeholder = appText(AppTextKey.SearchPlaceholder),
        searchContentDescription = null,
        searchIcon = Icons.Outlined.Search,
    )
    if (filterCatalog?.genreOptions?.isNotEmpty() == true) {
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filterCatalog.genreOptions) { option ->
                FilterChip(
                    selected = option.id in filters.includedGenreAliases,
                    onClick = {
                        val selected = option.id in filters.includedGenreAliases
                        onFiltersChange(
                            filters.copy(
                                includedGenreAliases = if (selected) {
                                    filters.includedGenreAliases - option.id
                                } else {
                                    filters.includedGenreAliases + option.id
                                },
                            ),
                        )
                    },
                    label = { Text(option.title) },
                )
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    SectionHeader(
        title = sectionTitle,
        actionLabel = appText(AppTextKey.SeeAll),
        onActionClick = { },
    )
    Spacer(Modifier.height(12.dp))
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 210.dp),
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items) { anime -> AnimeCatalogCard(anime, onClick = { onAnimeClick(anime) }) }
    }
}

@Composable
private fun AnimeCatalogCard(anime: Anime, onClick: () -> Unit) {
    AppPosterAnimeCard(
        anime = anime,
        metaText = listOf(anime.subtitle, "${anime.status} · ${anime.episodesLabel}")
            .filter(String::isNotBlank)
            .joinToString("\n"),
        onClick = onClick,
        posterContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = anime.title.take(1),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    )
}

@Composable
private fun AnimeDetailsPanel(anime: Anime, isLoading: Boolean, error: String?, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← ${appText(AppTextKey.Home)}") }
        androidx.compose.material3.Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(UiDimens.MediumCorner),
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(anime.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(anime.subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${anime.status} · ${anime.episodesLabel}", color = MaterialTheme.colorScheme.primary)
                if (isLoading) {
                    CircularProgressIndicator()
                }
                error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                anime.description?.let { description ->
                    Text(description, style = MaterialTheme.typography.bodyLarge)
                }
                if (anime.genres.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(anime.genres) { genre ->
                            FilterChip(selected = false, onClick = { }, label = { Text(genre) })
                        }
                    }
                }
                Button(onClick = { }) { Text(appText(AppTextKey.ExploreCatalog)) }
            }
        }
    }
}
