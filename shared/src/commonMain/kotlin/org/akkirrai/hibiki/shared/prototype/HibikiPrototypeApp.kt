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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppTonalSurface
import org.akkirrai.hibiki.shared.design.component.SectionHeader
import org.akkirrai.hibiki.shared.design.component.AppPosterAnimeCard
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogPresenter
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
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.library.LibraryCategory
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
import org.akkirrai.hibiki.shared.settings.AppSettingsCard
import org.akkirrai.hibiki.shared.settings.AppSettingsCardLabels
import org.akkirrai.hibiki.shared.text.DefaultAppTextResolver
import org.akkirrai.hibiki.shared.text.LocalAppTextResolver
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.search.AppSearchField

@Composable
fun HibikiAppShell(
    modifier: Modifier = Modifier,
    repository: AnimeCatalogRepository = PrototypeAnimeCatalogRepository,
    libraryRepository: LibraryRepository,
    profileRepository: LocalProfileDataRepository,
    settingsStore: AppSettingsStore = InMemoryAppSettingsStore(),
    systemLanguage: String = "en",
) {
    val scope = rememberCoroutineScope()
    val presenter = remember(repository) { AnimeCatalogPresenter(repository, scope) }
    val state by presenter.state.collectAsState()
    val libraryPresenter = remember(libraryRepository) { LibraryPresenter() }
    val libraryState by libraryPresenter.state.collectAsState()
    val profilePresenter = remember(profileRepository) { LocalProfilePresenter() }
    val profileState by profilePresenter.state.collectAsState()
    var selectedTab by remember { mutableStateOf(AppDestination.HOME) }
    val initialSettings = remember(settingsStore) { settingsStore.load() }
    var languageMode by remember(settingsStore) { mutableStateOf(initialSettings.languageMode) }
    var darkTheme by remember(settingsStore) { mutableStateOf(initialSettings.darkTheme) }
    var isEditingProfile by remember { mutableStateOf(false) }
    var editedProfileName by remember(profileState.data.profileName) { mutableStateOf(profileState.data.profileName) }

    DisposableEffect(presenter) {
        presenter.loadFilterCatalog()
        presenter.search()
        onDispose { presenter.close() }
    }

    LaunchedEffect(libraryRepository, state.selectedAnime) {
        libraryPresenter.updateEntries(libraryRepository.getEntries())
    }

    LaunchedEffect(profileRepository) {
        profilePresenter.load(profileRepository)
    }

    CompositionLocalProvider(
        LocalAppTextResolver provides DefaultAppTextResolver(languageMode, systemLanguage),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) HibikiDarkColorScheme else HibikiLightColorScheme,
            typography = HibikiTypography,
        ) {
            Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                BoxWithConstraints {
                    val compact = maxWidth < 760.dp
                    val onLanguageModeChange = { mode: LanguageMode ->
                        languageMode = mode
                        settingsStore.save(AppSettingsState(mode, darkTheme))
                    }
                    val onThemeChange = { dark: Boolean ->
                        darkTheme = dark
                        settingsStore.save(AppSettingsState(languageMode, dark))
                    }
                    if (compact) {
                    CompactAppLayout(
                        selectedTab,
                        { selectedTab = it },
                        state.query,
                        presenter::onQueryChange,
                        state.items,
                        state.filters,
                        state.filterCatalog,
                        presenter::updateFilters,
                        state.selectedAnime,
                        presenter::openDetails,
                        presenter::closeDetails,
                        state.isDetailsLoading,
                        state.detailsError,
                        libraryRepository,
                        languageMode,
                        onLanguageModeChange,
                        darkTheme,
                        onThemeChange,
                        libraryState.visibleEntries,
                        profileState.data,
                        isEditingProfile,
                        editedProfileName,
                        { editedProfileName = it },
                        { isEditingProfile = !isEditingProfile },
                        { profileRepository.updateProfileName(editedProfileName); profilePresenter.updateProfileName(editedProfileName); isEditingProfile = false },
                        { selectedTab = AppDestination.SETTINGS },
                        profileRepository,
                    )
                    } else {
                    WideAppLayout(
                        selectedTab,
                        { selectedTab = it },
                        state.query,
                        presenter::onQueryChange,
                        state.items,
                        state.filters,
                        state.filterCatalog,
                        presenter::updateFilters,
                        state.selectedAnime,
                        presenter::openDetails,
                        presenter::closeDetails,
                        state.isDetailsLoading,
                        state.detailsError,
                        libraryRepository,
                        languageMode,
                        onLanguageModeChange,
                        darkTheme,
                        onThemeChange,
                        libraryState.visibleEntries,
                        profileState.data,
                        isEditingProfile,
                        editedProfileName,
                        { editedProfileName = it },
                        { isEditingProfile = !isEditingProfile },
                        { profileRepository.updateProfileName(editedProfileName); profilePresenter.updateProfileName(editedProfileName); isEditingProfile = false },
                        { selectedTab = AppDestination.SETTINGS },
                        profileRepository,
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
    profileRepository: LocalProfileDataRepository,
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
            profileRepository,
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
    profileRepository: LocalProfileDataRepository,
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
            profileRepository,
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
    profileRepository: LocalProfileDataRepository,
    modifier: Modifier = Modifier,
) {
    if (selectedAnime != null) {
        AppDetailsScreen(
            anime = selectedAnime,
            onBackClick = onBackFromDetails,
            onRelatedAnimeClick = onAnimeClick,
            libraryRepository = libraryRepository,
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
                    query = query,
                    onQueryChange = onQueryChange,
                    items = items,
                    filters = filters,
                    filterCatalog = filterCatalog,
                    onFiltersChange = onFiltersChange,
                    onAnimeClick = onAnimeClick,
                )
                AppDestination.SEARCH -> SearchScreen(
                    query = query,
                    onQueryChange = onQueryChange,
                    items = items,
                    filters = filters,
                    filterCatalog = filterCatalog,
                    onFiltersChange = onFiltersChange,
                    onAnimeClick = onAnimeClick,
                )
                AppDestination.LIBRARY -> LibraryScreen(
                    entries = libraryEntries,
                    onAnimeClick = onAnimeClick,
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
                            overviewTab = if (appText(AppTextKey.Profile) == "Профиль") "Обзор" else "About",
                            activityTab = if (appText(AppTextKey.Profile) == "Профиль") "Активность" else "Active",
                            favoritesTab = if (appText(AppTextKey.Profile) == "Профиль") "Любимое" else "Favorites",
                            profileNameLabel = if (appText(AppTextKey.Profile) == "Профиль") "Имя" else "Name",
                            editContentDescription = if (appText(AppTextKey.Profile) == "Профиль") "Редактировать профиль" else "Edit profile",
                            saveContentDescription = if (appText(AppTextKey.Profile) == "Профиль") "Сохранить" else "Save",
                            changeAvatarContentDescription = if (appText(AppTextKey.Profile) == "Профиль") "Сменить аватар" else "Change avatar",
                            settingsContentDescription = appText(AppTextKey.Settings),
                            totalLabel = "TOTAL\nANIME",
                            daysLabel = "DAYS\nWATCHED",
                            timeLabel = "WATCH\nTIME",
                            recentTitle = if (appText(AppTextKey.Profile) == "Профиль") "Недавние" else "Recent",
                            recentEmptyText = "—",
                            favoritesEmptyText = if (appText(AppTextKey.Profile) == "Профиль") "Пока нет любимых тайтлов" else "No favourite titles yet",
                            analyticsWatchTitle = if (appText(AppTextKey.Profile) == "Профиль") "Время просмотра" else "Watch time",
                            analyticsTotalLabel = if (appText(AppTextKey.Profile) == "Профиль") "Всего" else "Total",
                            analyticsGenresTitle = if (appText(AppTextKey.Profile) == "Профиль") "Жанры" else "Genres",
                            analyticsGenresLabel = if (appText(AppTextKey.Profile) == "Профиль") "Жанров" else "Genres",
                            analyticsTitle = appText(AppTextKey.Profile),
                            episodesStatLabel = appText(AppTextKey.ProfileEpisodes),
                            watchStatLabel = if (appText(AppTextKey.Profile) == "Профиль") "Просмотр" else "Watched",
                            activityTitle = if (appText(AppTextKey.Profile) == "Профиль") "Активность" else "Activity",
                        ),
                        onNameChange = onProfileNameChange,
                        onAvatarEditClick = { },
                        onEditActionClick = if (isEditingProfile) onProfileSaveClick else onProfileEditClick,
                        onSettingsClick = onProfileSettingsClick,
                        avatarContent = { avatarModifier ->
                            profileData.profileAvatarUri?.let { ProfileAvatarImage(it) }
                                ?: ProfileAvatarPlaceholder(avatarModifier)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                AppDestination.SETTINGS -> SettingsScreen(
                    profileData = profileData,
                    languageMode = languageMode,
                    onLanguageModeChange = onLanguageModeChange,
                    darkTheme = darkTheme,
                    onThemeChange = onThemeChange,
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
private fun ColumnScope.HomeScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    onAnimeClick: (Anime) -> Unit,
) {
    CatalogScreenContent(
        query = query,
        onQueryChange = onQueryChange,
        items = items,
        filters = filters,
        filterCatalog = filterCatalog,
        onFiltersChange = onFiltersChange,
        onAnimeClick = onAnimeClick,
        sectionTitle = appText(AppTextKey.ContinueWatching),
    )
}

@Composable
private fun ColumnScope.SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    onAnimeClick: (Anime) -> Unit,
) {
    CatalogScreenContent(
        query = query,
        onQueryChange = onQueryChange,
        items = items,
        filters = filters,
        filterCatalog = filterCatalog,
        onFiltersChange = onFiltersChange,
        onAnimeClick = onAnimeClick,
        sectionTitle = appText(AppTextKey.ExploreCatalog),
    )
}

@Composable
private fun ColumnScope.LibraryScreen(
    entries: List<LibraryEntry>,
    onAnimeClick: (Anime) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = appText(AppTextKey.Library),
            actionLabel = appText(AppTextKey.SeeAll),
            onActionClick = { },
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 210.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(entries) { entry ->
                AnimeCatalogCard(entry.anime, onClick = { onAnimeClick(entry.anime) })
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    profileData: LocalProfileData,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))
        LocalProfileSummary(
            data = profileData,
            fallbackName = appText(AppTextKey.AppName),
            libraryLabel = appText(AppTextKey.ProfileLibrary),
            episodesLabel = appText(AppTextKey.ProfileEpisodes),
        )
        AppSettingsCard(
            languageMode = languageMode,
            onLanguageModeChange = onLanguageModeChange,
            darkTheme = darkTheme,
            onThemeChange = onThemeChange,
            labels = AppSettingsCardLabels(
                title = appText(AppTextKey.SettingsTitle),
                description = appText(AppTextKey.SettingsDescription),
                languageSystem = appText(AppTextKey.LanguageSystem),
                languageEnglish = appText(AppTextKey.LanguageEnglish),
                languageRussian = appText(AppTextKey.LanguageRussian),
                themeLight = appText(AppTextKey.ThemeLight),
                themeDark = appText(AppTextKey.ThemeDark),
            ),
        )
    }
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
