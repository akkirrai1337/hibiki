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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.isSystemInDarkTheme
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
import hibiki.shared.generated.resources.Res
import hibiki.shared.generated.resources.ic_discord
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import org.akkirrai.hibiki.shared.catalog.SourcesSearchPresenter
import org.akkirrai.hibiki.shared.catalog.AppCatalogScreen
import org.akkirrai.hibiki.shared.catalog.AppCatalogScreenLabels
import org.akkirrai.hibiki.shared.catalog.CatalogSort
import org.akkirrai.hibiki.shared.catalog.toAlias
import org.akkirrai.hibiki.shared.catalog.PrototypeAnimeCatalogRepository
import org.akkirrai.hibiki.shared.details.AppDetailsScreen
import org.akkirrai.hibiki.shared.details.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.shared.design.HibikiDarkColorScheme
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.design.component.AppSourceBadge
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.TitleWatchState
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
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.LocalProfilePresenter
import org.akkirrai.hibiki.shared.profile.LocalProfileSummary
import org.akkirrai.hibiki.shared.profile.AppLocalProfileLabels
import org.akkirrai.hibiki.shared.profile.AppLocalProfileScreen
import org.akkirrai.hibiki.shared.profile.ProfileAvatarImage
import org.akkirrai.hibiki.shared.profile.ProfileAvatarPlaceholder
import org.akkirrai.hibiki.shared.profile.LocalProfileSnapshotLabels
import org.akkirrai.hibiki.shared.profile.buildLocalProfileSnapshot
import org.akkirrai.hibiki.shared.profile.defaultProfileActivityDateStrings
import org.akkirrai.hibiki.shared.profile.profileActivityDateLabel
import org.akkirrai.hibiki.shared.profile.profileAddedDateLabel
import org.akkirrai.hibiki.shared.profile.profileRecentDateLabel
import org.akkirrai.hibiki.shared.profile.formatDurationHours
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.resolveAppLanguageTag
import org.akkirrai.hibiki.shared.settings.ThemeMode
import org.akkirrai.hibiki.shared.settings.AppSettingsState
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.settings.InMemoryAppSettingsStore
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.settings.DiscordRpcController
import org.akkirrai.hibiki.shared.settings.DiscordRpcUiState
import org.akkirrai.hibiki.shared.settings.AppDiscordAuthDialog
import org.akkirrai.hibiki.shared.settings.isBusy
import org.akkirrai.hibiki.shared.settings.resolveDiscordRpcStatusLabel
import org.akkirrai.hibiki.shared.settings.AppSettingsCard
import org.akkirrai.hibiki.shared.settings.AppSettingsCardLabels
import org.akkirrai.hibiki.shared.settings.AppSettingsScreen
import org.akkirrai.hibiki.shared.settings.AppSettingsScreenLabels
import org.akkirrai.hibiki.shared.text.DefaultAppTextResolver
import org.akkirrai.hibiki.shared.text.LocalAppTextResolver
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.text.appSearchResultsCount
import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppOverlay
import org.akkirrai.hibiki.shared.navigation.appBackHandlerEnabled
import org.akkirrai.hibiki.shared.navigation.appBottomBarVisible
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.navigation.AppTransitionKey
import org.akkirrai.hibiki.shared.navigation.appShellTransitionKey
import org.akkirrai.hibiki.shared.navigation.currentRoute
import org.akkirrai.hibiki.shared.navigation.reduce
import org.akkirrai.hibiki.shared.navigation.transitionKey
import org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination
import org.akkirrai.hibiki.shared.search.AppSearchField
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.AppLocalSourcesScreen
import org.akkirrai.hibiki.shared.source.AppSourceIconImage
import org.akkirrai.hibiki.shared.source.SourcesSearchUiState
import org.akkirrai.hibiki.shared.onboarding.AppOnboardingScreen
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.hibiki.shared.player.AppWatchSourcesContent
import org.akkirrai.hibiki.shared.player.AppEpisodesContent
import org.akkirrai.hibiki.shared.player.AppPlaybackOverlayHost
import org.akkirrai.hibiki.shared.player.PlayerPresenter
import org.akkirrai.hibiki.shared.player.PlayerUiState
import org.akkirrai.hibiki.shared.player.EpisodesScreenState
import org.akkirrai.hibiki.shared.player.EpisodesUiState
import org.akkirrai.hibiki.shared.player.EpisodeRow
import org.akkirrai.hibiki.shared.player.buildEpisodeRowHeadline
import org.akkirrai.hibiki.shared.player.resolveEpisodeProgressStatus
import org.akkirrai.hibiki.shared.player.WatchScreenScaffold
import org.akkirrai.hibiki.shared.player.WatchDataRepository
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.EpisodeDownloadRepository
import org.akkirrai.hibiki.shared.player.OfflineWatchDataRepository
import org.akkirrai.hibiki.shared.player.EpisodeDownloadState
import org.akkirrai.hibiki.shared.player.EpisodeDownloadActionState
import org.akkirrai.hibiki.shared.player.AppEpisodeDownloadRowContent
import org.akkirrai.hibiki.shared.player.AppEpisodesDownloadToggle
import org.akkirrai.hibiki.shared.player.rememberEpisodesDownloadControlsVisible
import org.akkirrai.hibiki.shared.player.toEpisodeDownloadActionState
import org.akkirrai.hibiki.shared.player.keepsTitleSaved
import org.akkirrai.hibiki.shared.player.EpisodesDownloadToggleEndPadding
import org.akkirrai.hibiki.shared.player.EpisodesDownloadToggleTopPadding
import org.akkirrai.hibiki.shared.player.WatchSourcesPresenter
import org.akkirrai.hibiki.shared.player.WatchSourcesScreenState
import org.akkirrai.hibiki.shared.player.errorEpisodesState
import org.akkirrai.hibiki.shared.player.initialEpisodesState
import org.akkirrai.hibiki.shared.player.initialWatchSourcesState
import org.akkirrai.hibiki.shared.player.loadedEpisodesState
import org.akkirrai.hibiki.shared.player.beginPlaybackLoad
import org.akkirrai.hibiki.shared.player.withPlaybackError
import org.akkirrai.hibiki.shared.player.PlaybackRequest
import org.akkirrai.hibiki.shared.player.resolvePlaybackPreferences
import org.akkirrai.hibiki.shared.player.AppPlayerErrorOverlay
import org.akkirrai.hibiki.shared.player.AppPlayerLoadingOverlay
import org.akkirrai.hibiki.shared.player.resetForNavigation
import org.akkirrai.hibiki.shared.player.withPlaybackLoaded
import org.akkirrai.hibiki.shared.player.withLoadedSources
import org.akkirrai.hibiki.shared.player.withWatchSourcesError
import org.akkirrai.hibiki.shared.player.resolveResumeWatchState
import org.akkirrai.hibiki.shared.player.showAllWatchSources
import org.akkirrai.hibiki.shared.model.WatchSource
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackRoute
import org.akkirrai.hibiki.shared.platform.AppSystemBackHandler

private const val DEFAULT_PROFILE_NAME = "hibiki"
private const val HOME_SEARCH_DEBOUNCE_MS = 450L
private const val HOME_SEARCH_MIN_QUERY_LENGTH = 3
private const val HOME_SEARCH_PAGE_SIZE = 24

@Composable
fun HibikiAppShell(
    modifier: Modifier = Modifier,
    repository: AnimeCatalogRepository = PrototypeAnimeCatalogRepository,
    homeRepository: HomeDataRepository? = null,
    libraryRepository: LibraryRepository,
    profileRepository: LocalProfileDataRepository,
    settingsStore: AppSettingsStore = InMemoryAppSettingsStore(),
    progressRepository: PlaybackProgressRepository? = null,
    episodeDownloadRepository: EpisodeDownloadRepository? = null,
    offlineWatchDataRepository: OfflineWatchDataRepository? = null,
    offlineTitleMetadataRepository: OfflineTitleMetadataRepository? = null,
    resumeFrameContent: (@Composable (String, Modifier) -> Unit)? = null,
    systemLanguage: String = "en",
    appVersionName: String = "dev",
    enableOnboarding: Boolean = false,
    onboardingNotificationPermissionState: NotificationPermissionState = NotificationPermissionState.NOT_ASKED,
    onRequestOnboardingNotificationPermission: () -> Unit = {},
    onConfigureNotifications: () -> Unit = {},
    notificationsAvailable: Boolean = true,
    onCheckForUpdates: () -> Unit = {},
    onExportLogs: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onProfileAvatarEdit: (((String) -> Unit) -> Unit) = {},
    profileAvatarEditAvailable: Boolean = false,
    onGitHubClick: () -> Unit = {},
    discordRpcController: DiscordRpcController? = null,
    onDiscordBrowserSignIn: (((String) -> Unit) -> Unit) = {},
    sources: List<AppSourceDescriptor> = emptyList(),
    selectedSourceId: String? = null,
    onSourceSelected: (String) -> Unit = {},
    onWatchSourceSelected: (String, org.akkirrai.hibiki.shared.model.WatchSource) -> Unit = { _, _ -> },
    watchRepository: WatchDataRepository? = null,
    onPlaybackReady: (org.akkirrai.hibiki.shared.model.PlaybackStream, org.akkirrai.hibiki.shared.model.PlaybackContext) -> Unit = { _, _ -> },
    onPlaybackSelectionChanged: (org.akkirrai.hibiki.shared.model.PlaybackSelection) -> Unit = {},
    loadPlaybackSelection: (String) -> org.akkirrai.hibiki.shared.model.PlaybackSelection? = { null },
    playbackHost: (@Composable (org.akkirrai.hibiki.shared.model.PlaybackStream, org.akkirrai.hibiki.shared.model.PlaybackContext, AppNavigationState, () -> Unit, (WatchEpisode) -> Unit, (PlaybackSettingsAction) -> Unit, (AppNavigationEvent) -> Unit) -> Unit)? = null,
    playerWindowMode: @Composable (Boolean) -> Unit = {},
    showSettingsBackButton: Boolean = false,
    includeNavigationBarPadding: Boolean = true,
    applyStatusBarPadding: Boolean = false,
) {
    val scope = rememberCoroutineScope {
        CoroutineExceptionHandler { _, throwable ->
            if (throwable !is CancellationException) {
                println("Hibiki coroutine failed: ${throwable.message ?: throwable::class.simpleName}")
            }
        }
    }
    val presenter = remember(repository) { AnimeCatalogPresenter(repository, scope, pageSize = HOME_SEARCH_PAGE_SIZE) }
    val state by presenter.state.collectAsState()
    val homePresenter = remember(homeRepository) { HomePresenter() }
    val homeState by homePresenter.state.collectAsState()
    val catalogListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val sourceSearchPresenter = remember(repository, sources) { SourcesSearchPresenter(repository, sources, scope) }
    val sourceSearchState by sourceSearchPresenter.state.collectAsState()
    val watchPresenter = remember(watchRepository) { WatchSourcesPresenter() }
    val watchState by watchPresenter.state.collectAsState()
    var watchAnime by remember { mutableStateOf<Anime?>(null) }
    var detailsAnime by remember { mutableStateOf<Anime?>(null) }
    var detailsResumeState by remember { mutableStateOf<TitleWatchState?>(null) }
    var watchLoadGeneration by remember { mutableStateOf(0) }
    var episodesLoadGeneration by remember { mutableStateOf(0) }
    val episodesPresenter = remember(watchRepository) { org.akkirrai.hibiki.shared.player.EpisodesPresenter() }
    val episodesState by episodesPresenter.state.collectAsState()
    var navigationState by remember { mutableStateOf(AppNavigationState()) }
    val selectedWatchSource = navigationState.backStack.asReversed()
        .mapNotNull { (it as? AppRoute.Episodes)?.source }
        .firstOrNull()
    val playerPresenter = remember(watchRepository) { PlayerPresenter(PlayerUiState(isLoading = false)) }
    val playerState by playerPresenter.state.collectAsState()
    var playbackRequestGeneration by remember { mutableStateOf(0) }
    var playbackJob by remember { mutableStateOf<Job?>(null) }
    var activePlaybackRoute by remember { mutableStateOf<PlaybackRoute?>(null) }
    var pendingPlaybackContext by remember { mutableStateOf<PlaybackContext?>(null) }
    val libraryPresenter = remember(libraryRepository) { LibraryPresenter() }
    val libraryState by libraryPresenter.state.collectAsState()
    val profilePresenter = remember(profileRepository) { LocalProfilePresenter() }
    val profileState by profilePresenter.state.collectAsState()
    val discordRpcState by (discordRpcController?.state ?: kotlinx.coroutines.flow.MutableStateFlow(DiscordRpcUiState())).collectAsState()
    var pendingDiscordToken by remember { mutableStateOf<String?>(null) }
    val discordAuthOverlay = AppOverlay.Dialog("discord-auth")
    val isDiscordAuthDialogOpen = navigationState.overlays.lastOrNull() == discordAuthOverlay

    fun openDiscordAuthDialog() {
        if (!isDiscordAuthDialogOpen) {
            navigationState = navigationState.reduce(
                AppNavigationEvent.PresentOverlay(discordAuthOverlay),
            )
        }
    }

    fun closeDiscordAuthDialog() {
        if (isDiscordAuthDialogOpen) {
            navigationState = navigationState.reduce(AppNavigationEvent.DismissOverlay)
        }
    }
    var selectedTab by remember { mutableStateOf(AppDestination.HOME) }
    var homeSearchQuery by remember { mutableStateOf("") }
    val initialSettings = remember(settingsStore) { settingsStore.load() }
    var languageMode by remember(settingsStore) { mutableStateOf(initialSettings.languageMode) }
    var darkTheme by remember(settingsStore) { mutableStateOf(initialSettings.darkTheme) }
    var themeMode by remember(settingsStore) { mutableStateOf(initialSettings.themeMode) }
    var useSystemColorScheme by remember(settingsStore) { mutableStateOf(initialSettings.useSystemColorScheme) }
    var useAmoledTheme by remember(settingsStore) { mutableStateOf(initialSettings.useAmoledTheme) }
    var autoSkipSegments by remember(settingsStore) { mutableStateOf(initialSettings.autoSkipSegments) }
    var autoPlayNextEpisode by remember(settingsStore) { mutableStateOf(initialSettings.autoPlayNextEpisode) }
    var onboardingCompleted by remember(settingsStore) { mutableStateOf(initialSettings.onboardingCompleted) }
    var onboardingSourceId by remember(settingsStore) {
        mutableStateOf(initialSettings.selectedSourceId ?: selectedSourceId)
    }
    var currentSelectedSourceId by remember(settingsStore, selectedSourceId) {
        mutableStateOf(initialSettings.selectedSourceId ?: selectedSourceId)
    }
    var isEditingProfile by remember { mutableStateOf(false) }
    var editedProfileName by remember(profileState.data.profileName) {
        mutableStateOf(profileState.data.profileName.ifBlank { DEFAULT_PROFILE_NAME })
    }

    DisposableEffect(presenter) {
        presenter.loadFilterCatalog()
        presenter.search()
        onDispose {
            presenter.close()
            sourceSearchPresenter.close()
        }
    }

    LaunchedEffect(homeSearchQuery) {
        val query = homeSearchQuery.trim()
        if (query.length < HOME_SEARCH_MIN_QUERY_LENGTH) return@LaunchedEffect
        delay(HOME_SEARCH_DEBOUNCE_MS)
        presenter.onQueryChange(query)
    }

    LaunchedEffect(libraryRepository, state.selectedAnime) {
        try {
            libraryPresenter.updateEntries(libraryRepository.getEntries())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            libraryPresenter.updateEntries(emptyList())
        }
    }

    LaunchedEffect(homeRepository) {
        try {
            if (homeRepository == null) {
                homePresenter.setState(HomeUiState())
            } else {
                homePresenter.setState(homeRepository.fallbackHomeState())
                homePresenter.setState(homeRepository.loadHomeState())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            homePresenter.setState(HomeUiState())
        }
    }

    LaunchedEffect(profileRepository) {
        try {
            profilePresenter.load(profileRepository)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            profilePresenter.setData(LocalProfileData())
        }
    }

    LaunchedEffect(progressRepository, state.selectedAnime?.id) {
        val animeId = state.selectedAnime?.id
        detailsResumeState = if (progressRepository != null && animeId != null) {
            resolveResumeWatchState(
                progressRepository.getAllPlaybackProgress().filter { it.titleId == animeId },
            )
        } else {
            null
        }
    }

    LaunchedEffect(offlineTitleMetadataRepository, state.selectedAnime?.id) {
        val selected = state.selectedAnime
        detailsAnime = selected?.let { anime ->
            offlineTitleMetadataRepository?.get(anime.id) ?: anime
        }
    }

    LaunchedEffect(offlineTitleMetadataRepository, state.selectedAnime, state.isDetailsLoading) {
        val selected = state.selectedAnime ?: return@LaunchedEffect
        if (!state.isDetailsLoading) {
            offlineTitleMetadataRepository?.save(selected)
            detailsAnime = selected
        }
    }

    LaunchedEffect(watchRepository, watchAnime?.id, watchLoadGeneration) {
        val repositoryForWatch = watchRepository ?: return@LaunchedEffect
        val anime = watchAnime ?: return@LaunchedEffect
        watchPresenter.setState(
            initialWatchSourcesState(
                cachedSources = null,
                offlineSources = offlineWatchDataRepository?.getOfflineSources(anime.id).orEmpty(),
                forceRefresh = true,
            ),
        )
        runCatching { repositoryForWatch.loadSources(anime.id) }
            .onSuccess { sourcesForWatch ->
                watchPresenter.update { state ->
                    state.withLoadedSources(
                        sources = sourcesForWatch,
                        offlineSources = offlineWatchDataRepository?.getOfflineSources(anime.id).orEmpty(),
                        isLoading = false,
                    )
                }
            }
            .onFailure { error ->
                watchPresenter.update {
                    it.withWatchSourcesError(error.message ?: "Unable to load watch sources")
                }
            }
    }

    LaunchedEffect(watchRepository, activePlaybackRoute?.context?.episodeId) {
        val repositoryForPlayback = watchRepository ?: return@LaunchedEffect
        val route = activePlaybackRoute ?: return@LaunchedEffect
        val options = runCatching {
            repositoryForPlayback.getPlaybackSettingsOptions(
                sourceId = route.context.sourceId,
                episodeId = route.context.episodeId,
            )
        }.getOrNull() ?: return@LaunchedEffect
        if (activePlaybackRoute?.context?.episodeId == route.context.episodeId) {
            activePlaybackRoute = route.copy(
                context = route.context.copy(settingsOptions = options),
            )
        }
    }

    LaunchedEffect(watchRepository, selectedWatchSource?.sourceId, episodesLoadGeneration) {
        val repositoryForWatch = watchRepository ?: return@LaunchedEffect
        val source = selectedWatchSource ?: return@LaunchedEffect
        val offlineEpisodes = offlineWatchDataRepository?.getOfflineEpisodes(source.sourceId).orEmpty()
        episodesPresenter.setState(initialEpisodesState(offlineEpisodes))
        runCatching { repositoryForWatch.getEpisodes(source.sourceId) }
            .onSuccess { episodes ->
                episodesPresenter.setState(loadedEpisodesState(episodes, offlineEpisodes))
            }
            .onFailure { error ->
                episodesPresenter.setState(
                    errorEpisodesState(error.message ?: "Unable to load episodes", offlineEpisodes),
                )
            }
    }

    fun resetPlayerState() {
        playerPresenter.setState(playerPresenter.state.value.resetForNavigation())
    }

    fun requestPlayback(
        episode: WatchEpisode,
        sourceOverride: WatchSource? = null,
        preferredPlayerName: String? = null,
        preferredQuality: String? = null,
        episodesOverride: List<WatchEpisode>? = null,
        replacePlayerRoute: Boolean = false,
    ) {
        val repositoryForPlayback = watchRepository
        val sourceForPlayback = sourceOverride ?: selectedWatchSource
        if (repositoryForPlayback == null || sourceForPlayback == null) return

        val effectivePreferences = resolvePlaybackPreferences(
            sourceId = sourceForPlayback.sourceId,
            savedSelection = loadPlaybackSelection(watchAnime?.id.orEmpty()),
            explicitPlayerName = preferredPlayerName,
            explicitQuality = preferredQuality,
            allowSavedSelection = sourceOverride == null &&
                preferredPlayerName == null &&
                preferredQuality == null,
        )
        val effectivePlayerName = effectivePreferences.playerName
        val effectiveQuality = effectivePreferences.quality
        val requestEpisodes = episodesOverride
            ?: (episodesState.result as? EpisodesUiState.Content)?.items.orEmpty()
        val requestContext = PlaybackContext(
            titleId = watchAnime?.id.orEmpty(),
            sourceId = sourceForPlayback.sourceId,
            episodeId = episode.id,
            episodeNumber = episode.number,
            sourceTitle = sourceForPlayback.title,
            episodes = requestEpisodes,
            selectedPlayerName = effectivePlayerName,
            selectedQualityLabel = effectiveQuality ?: sourceForPlayback.qualityLabel,
        )
        val playerRoute = AppRoute.Player(
            sourceId = sourceForPlayback.sourceId,
            episodeId = episode.id,
            episodeNumber = episode.number,
        )
        pendingPlaybackContext = requestContext
        navigationState = navigationState.reduce(
            if (navigationState.currentRoute is AppRoute.Player) {
                AppNavigationEvent.Replace(playerRoute)
            } else {
                AppNavigationEvent.Navigate(playerRoute)
            },
        )

        playbackJob?.cancel()
        val requestGeneration = playbackRequestGeneration + 1
        playbackRequestGeneration = requestGeneration
        playerPresenter.update {
            it.copy(
                currentSourceId = sourceForPlayback.sourceId,
                currentEpisodeId = episode.id,
                currentEpisodeNumber = episode.number,
                lastPlaybackRequest = PlaybackRequest(
                    episode = episode,
                    source = sourceForPlayback,
                    preferredPlayerName = effectivePlayerName,
                    preferredQuality = effectiveQuality,
                ),
            ).beginPlaybackLoad(emptySet())
        }
        playbackJob = scope.launch {
            val result = runCatching {
                repositoryForPlayback.resolvePlayback(
                    sourceId = sourceForPlayback.sourceId,
                    episodeId = episode.id,
                    preferredQuality = effectiveQuality ?: sourceForPlayback.qualityLabel,
                    preferredPlayerName = effectivePlayerName,
                )
            }
            if (requestGeneration != playbackRequestGeneration) return@launch
            result.onSuccess { playback ->
                val loadedEpisodes = requestEpisodes
                playerPresenter.update {
                    it.withPlaybackLoaded(
                        stream = playback,
                        episodes = loadedEpisodes,
                        episodeId = episode.id,
                        episodeNumber = episode.number,
                        savedSeekMs = progressRepository
                            ?.getPlaybackProgress(watchAnime?.id.orEmpty(), episode.id)
                            ?.let { progress ->
                                org.akkirrai.hibiki.shared.player.resolveResumablePlaybackPosition(
                                    progress.positionMs,
                                    progress.durationMs,
                                )
                            },
                    )
                }
                navigationState = navigationState.reduce(AppNavigationEvent.Replace(playerRoute))
                val context = requestContext.copy(episodes = loadedEpisodes)
                pendingPlaybackContext = null
                onPlaybackSelectionChanged(
                    org.akkirrai.hibiki.shared.model.PlaybackSelection(
                        titleId = context.titleId,
                        sourceId = context.sourceId,
                        sourceTitle = context.sourceTitle,
                        quality = playback.qualityLabel ?: context.selectedQualityLabel,
                        playerName = context.selectedPlayerName,
                    ),
                )
                if (playbackHost != null) {
                    activePlaybackRoute = PlaybackRoute(playback, context)
                } else {
                    onPlaybackReady(playback, context)
                }
            }.onFailure { error ->
                playerPresenter.update {
                    it.withPlaybackError(
                        message = error.message ?: "Unable to resolve playback",
                        episodes = it.episodes,
                        episodeId = episode.id,
                        episodeNumber = episode.number,
                    )
                }
                println("Hibiki playback resolution failed: ${error.message ?: error::class.simpleName}")
            }
            playbackJob = null
        }
    }

    fun handlePlaybackSettingsAction(action: PlaybackSettingsAction) {
        val route = activePlaybackRoute ?: return
        val repositoryForPlayback = watchRepository ?: return
        when (action) {
            is PlaybackSettingsAction.SelectVoiceover -> scope.launch {
                val episodes = runCatching { repositoryForPlayback.getEpisodes(action.source.sourceId) }
                    .getOrNull()
                    ?: return@launch
                val episode = episodes.firstOrNull { it.number == route.context.episodeNumber }
                    ?: episodes.firstOrNull()
                    ?: return@launch
                requestPlayback(
                    episode = episode,
                    sourceOverride = action.source,
                    preferredQuality = action.source.qualityLabel,
                    episodesOverride = episodes,
                    replacePlayerRoute = true,
                )
            }
            is PlaybackSettingsAction.SelectPlayer -> requestPlayback(
                episode = WatchEpisode(
                    id = route.context.episodeId,
                    number = route.context.episodeNumber,
                    title = null,
                ),
                sourceOverride = WatchSource(
                    sourceId = route.context.sourceId,
                    title = route.context.sourceTitle,
                    episodeCount = route.context.episodes.size,
                    qualityLabel = route.context.selectedQualityLabel,
                ),
                preferredPlayerName = action.playerName,
                preferredQuality = route.context.selectedQualityLabel,
                episodesOverride = route.context.episodes,
                replacePlayerRoute = true,
            )
            is PlaybackSettingsAction.SelectQuality -> requestPlayback(
                episode = WatchEpisode(
                    id = route.context.episodeId,
                    number = route.context.episodeNumber,
                    title = null,
                ),
                sourceOverride = WatchSource(
                    sourceId = route.context.sourceId,
                    title = route.context.sourceTitle,
                    episodeCount = route.context.episodes.size,
                    qualityLabel = action.qualityLabel,
                ),
                preferredPlayerName = route.context.selectedPlayerName,
                preferredQuality = action.qualityLabel,
                episodesOverride = route.context.episodes,
                replacePlayerRoute = true,
            )
            is PlaybackSettingsAction.SetAutoSkipSegments -> {
                autoSkipSegments = action.enabled
                settingsStore.save(settingsStore.load().copy(autoSkipSegments = action.enabled))
            }
            is PlaybackSettingsAction.SetAutoPlayNextEpisode -> {
                autoPlayNextEpisode = action.enabled
                settingsStore.save(settingsStore.load().copy(autoPlayNextEpisode = action.enabled))
            }
        }
    }

    val refreshLocalData = {
        scope.launch {
            try {
                libraryPresenter.updateEntries(libraryRepository.getEntries())
                profilePresenter.load(profileRepository)
                homeRepository?.let { repository ->
                    homePresenter.setState(repository.loadHomeState())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                println("Hibiki local data refresh failed: ${throwable.message ?: throwable::class.simpleName}")
            }
        }
        Unit
    }

    fun handleSystemBack() {
        if (navigationState.overlays.isNotEmpty()) {
            navigationState = navigationState.reduce(AppNavigationEvent.Back)
            return
        }
        if (selectedTab == AppDestination.SETTINGS) {
            if (navigationState.currentRoute is AppRoute.Settings) {
                navigationState = navigationState.reduce(AppNavigationEvent.Back)
            }
            selectedTab = AppDestination.PROFILE
            return
        }
        if (activePlaybackRoute != null) {
            activePlaybackRoute = null
            pendingPlaybackContext = null
            navigationState = navigationState.reduce(AppNavigationEvent.Back)
            return
        }
        if (navigationState.backStack.isEmpty()) return
        val routeBeforeBack = navigationState.currentRoute
        navigationState = navigationState.reduce(AppNavigationEvent.Back)
        when (routeBeforeBack) {
            is AppRoute.Player -> {
                playbackJob?.cancel()
                playbackJob = null
                pendingPlaybackContext = null
                resetPlayerState()
            }
            is AppRoute.Episodes, is AppRoute.WatchSources -> {
                playbackJob?.cancel()
                playbackJob = null
                playbackRequestGeneration++
                episodesPresenter.setState(EpisodesScreenState())
                resetPlayerState()
                if (navigationState.currentRoute !is AppRoute.Episodes &&
                    navigationState.currentRoute !is AppRoute.WatchSources
                ) {
                    watchAnime = null
                }
            }
            is AppRoute.Details -> presenter.closeDetails()
            else -> Unit
        }
    }

    fun openDetails(anime: Anime) {
        presenter.openDetails(anime)
        val currentRoute = navigationState.currentRoute
        if (currentRoute !is AppRoute.Details || currentRoute.animeId != anime.id) {
            navigationState = navigationState.reduce(
                AppNavigationEvent.Navigate(AppRoute.Details(anime.id)),
            )
        }
    }

    fun closeDetails() {
        if (navigationState.currentRoute is AppRoute.Details) {
            navigationState = navigationState.reduce(AppNavigationEvent.Back)
        }
        presenter.closeDetails()
    }

    CompositionLocalProvider(
        LocalAppTextResolver provides DefaultAppTextResolver(languageMode, systemLanguage),
    ) {
        val effectiveDarkTheme = when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val baseColorScheme = if (effectiveDarkTheme) HibikiDarkColorScheme else HibikiLightColorScheme
        val colorScheme = if (useAmoledTheme && effectiveDarkTheme) {
            baseColorScheme.copy(
                background = androidx.compose.ui.graphics.Color.Black,
                surface = androidx.compose.ui.graphics.Color.Black,
            )
        } else {
            baseColorScheme
        }
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HibikiTypography,
        ) {
            Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                playerWindowMode(navigationState.currentRoute is AppRoute.Player)
                AppSystemBackHandler(
                    enabled = appBackHandlerEnabled(
                        selectedTab = selectedTab,
                        currentRoute = navigationState.currentRoute,
                        hasOverlay = navigationState.overlays.isNotEmpty(),
                    ),
                    onBack = ::handleSystemBack,
                ) {
                Box {
                    fun saveSettings() {
                        settingsStore.save(
                            settingsStore.load().copy(
                                languageMode = languageMode,
                                darkTheme = darkTheme,
                                themeMode = themeMode,
                                useSystemColorScheme = useSystemColorScheme,
                                useAmoledTheme = useAmoledTheme,
                                autoSkipSegments = autoSkipSegments,
                                autoPlayNextEpisode = autoPlayNextEpisode,
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
                    val activeDownloadMode = when (val route = navigationState.currentRoute) {
                        is AppRoute.WatchSources -> route.downloadMode
                        is AppRoute.Episodes -> route.downloadMode
                        else -> false
                    }
                    fun selectRootTab(destination: AppDestination) {
                        val target = when (destination) {
                            AppDestination.HOME -> AppTopLevelDestination.HOME
                            AppDestination.CATALOG -> AppTopLevelDestination.CATALOG
                            AppDestination.LIBRARY -> AppTopLevelDestination.LIBRARY
                            AppDestination.SOURCES -> AppTopLevelDestination.SOURCES
                            AppDestination.PROFILE, AppDestination.SETTINGS -> AppTopLevelDestination.PROFILE
                        }
                        if (target == topLevelDestination && selectedTab == destination) return
                        navigationState = navigationState.reduce(
                            AppNavigationEvent.SelectTopLevel(target),
                        )
                        selectedTab = destination
                        presenter.clearDetails()
                        detailsAnime = null
                        watchAnime = null
                        playbackJob?.cancel()
                        playbackJob = null
                        playbackRequestGeneration++
                        activePlaybackRoute = null
                        pendingPlaybackContext = null
                        episodesPresenter.setState(EpisodesScreenState())
                        resetPlayerState()
                    }
                    if (!enableOnboarding || onboardingCompleted) {
                    AppProductionRoot(
                        currentDestination = topLevelDestination,
                            onNavigationEvent = { event ->
                                if (event is AppNavigationEvent.SelectTopLevel) {
                                    selectRootTab(
                                        when (event.destination) {
                                            AppTopLevelDestination.HOME -> AppDestination.HOME
                                            AppTopLevelDestination.CATALOG -> AppDestination.CATALOG
                                            AppTopLevelDestination.LIBRARY -> AppDestination.LIBRARY
                                            AppTopLevelDestination.SOURCES -> AppDestination.SOURCES
                                            AppTopLevelDestination.PROFILE -> AppDestination.PROFILE
                                        },
                                    )
                                }
                            },
                        showBottomBar = appBottomBarVisible(
                            selectedTab = selectedTab,
                            currentRoute = navigationState.currentRoute,
                        ),
                        includeNavigationBarPadding = includeNavigationBarPadding,
                        contentTransitionKey = appShellTransitionKey(
                            topLevelDestination = topLevelDestination,
                            selectedTab = selectedTab.name,
                            detailsId = state.selectedAnime?.id,
                            watchId = watchAnime?.id,
                            sourceId = selectedWatchSource?.sourceId,
                            routeKey = navigationState.currentRoute.transitionKey(),
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier),
                    ) { animatedDestination ->
                        val animatedTab = when (animatedDestination) {
                            AppTopLevelDestination.HOME -> AppDestination.HOME
                            AppTopLevelDestination.CATALOG -> AppDestination.CATALOG
                            AppTopLevelDestination.LIBRARY -> AppDestination.LIBRARY
                            AppTopLevelDestination.SOURCES -> AppDestination.SOURCES
                            AppTopLevelDestination.PROFILE -> if (selectedTab == AppDestination.SETTINGS) {
                                AppDestination.SETTINGS
                            } else {
                                AppDestination.PROFILE
                            }
                        }
                        AppDestinationContent(
                            selectedTab = animatedTab,
                            episodeDownloadRepository = episodeDownloadRepository,
                            offlineWatchDataRepository = offlineWatchDataRepository,
                            offlineTitleMetadataRepository = offlineTitleMetadataRepository,
                            resumeFrameContent = resumeFrameContent,
                            downloadMode = activeDownloadMode,
                            systemLanguage = systemLanguage,
                            appVersionName = appVersionName,
                            catalogState = state,
                            homeState = homeState,
                            onHomeRefresh = {
                                homeRepository?.let { repo ->
                                    scope.launch {
                                        try {
                                            homePresenter.setState(homePresenter.state.value.copy(isLoading = true))
                                            homePresenter.setState(repo.refreshHomeState())
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (throwable: Throwable) {
                                            println("Hibiki home refresh failed: ${throwable.message ?: throwable::class.simpleName}")
                                            homePresenter.setState(homePresenter.state.value.copy(isLoading = false))
                                        }
                                    }
                                }
                            },
                            catalogListState = catalogListState,
                            query = state.query,
                            onQueryChange = presenter::onQueryChange,
                            homeQuery = homeSearchQuery,
                            onHomeQueryChange = { homeSearchQuery = it },
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
                            onBrowseCatalog = { selectRootTab(AppDestination.CATALOG) },
                            onOpenLibrary = { selectRootTab(AppDestination.LIBRARY) },
                            selectedAnime = detailsAnime ?: state.selectedAnime,
                            detailsResumeState = detailsResumeState,
                            onAnimeClick = ::openDetails,
                            onBackFromDetails = ::closeDetails,
                            isDetailsLoading = state.isDetailsLoading,
                            detailsError = state.detailsError,
                            watchAnime = watchAnime,
                            onWatchClick = { anime ->
                                watchPresenter.setState(
                                    initialWatchSourcesState(
                                        cachedSources = null,
                                        offlineSources = offlineWatchDataRepository?.getOfflineSources(anime.id).orEmpty(),
                                        forceRefresh = true,
                                    ),
                                )
                                episodesPresenter.setState(EpisodesScreenState())
                                playbackJob?.cancel()
                                playbackJob = null
                                playbackRequestGeneration++
                                resetPlayerState()
                                watchAnime = anime
                                navigationState = navigationState.reduce(
                                    AppNavigationEvent.Navigate(
                                        AppRoute.WatchSources(anime.id, downloadMode = activeDownloadMode),
                                    ),
                                )
                            },
                            onBackFromWatch = {
                                playbackJob?.cancel()
                                playbackJob = null
                                playbackRequestGeneration++
                                navigationState = navigationState.reduce(AppNavigationEvent.Back)
                                if (navigationState.currentRoute is AppRoute.Episodes ||
                                    navigationState.currentRoute is AppRoute.WatchSources
                                ) {
                                    episodesPresenter.setState(EpisodesScreenState())
                                    resetPlayerState()
                                    return@AppDestinationContent
                                }
                                resetPlayerState()
                                watchAnime = null
                            },
                            watchState = watchState,
                            episodesState = episodesState,
                            selectedWatchSource = selectedWatchSource,
                            playbackError = playerState.errorMessage,
                            playbackLoading = playerState.isLoading,
                            onWatchRetry = {
                                val failedRequest = playerState.lastPlaybackRequest
                                if (playerState.errorMessage != null && failedRequest != null) {
                                    requestPlayback(
                                        episode = failedRequest.episode,
                                        sourceOverride = failedRequest.source,
                                        preferredPlayerName = failedRequest.preferredPlayerName,
                                        preferredQuality = failedRequest.preferredQuality,
                                    )
                                } else if (selectedWatchSource == null) {
                                    watchLoadGeneration++
                                } else {
                                    episodesLoadGeneration++
                                }
                            },
                            onWatchLoadMore = { watchPresenter.update(WatchSourcesScreenState::showAllWatchSources) },
                            onWatchSourceClick = { source ->
                                onWatchSourceSelected(watchAnime?.id.orEmpty(), source)
                                playbackJob?.cancel()
                                playbackJob = null
                                playbackRequestGeneration++
                                resetPlayerState()
                                navigationState = navigationState.reduce(
                                    AppNavigationEvent.Navigate(
                                        AppRoute.Episodes(
                                            source,
                                            downloadMode = activeDownloadMode,
                                            animeId = watchAnime?.id,
                                        ),
                                    ),
                                )
                            },
                            onWatchEpisodeClick = ::requestPlayback,
                            onResumePlayback = { progress ->
                                watchAnime = state.selectedAnime
                                requestPlayback(
                                    episode = WatchEpisode(
                                        id = progress.episodeId,
                                        number = progress.episodeNumber,
                                        title = null,
                                    ),
                                    sourceOverride = WatchSource(
                                        sourceId = progress.sourceId,
                                        title = progress.sourceTitle,
                                        episodeCount = null,
                                        qualityLabel = progress.quality,
                                    ),
                                    preferredQuality = progress.quality,
                                )
                            },
                            watchRepositoryAvailable = watchRepository != null,
                            libraryRepository = libraryRepository,
                            languageMode = languageMode,
                            onLanguageModeChange = onLanguageModeChange,
                            darkTheme = darkTheme,
                            onThemeChange = onThemeChange,
                            themeMode = themeMode,
                            onThemeModeChange = { mode ->
                                themeMode = mode
                                if (mode != ThemeMode.SYSTEM) {
                                    darkTheme = mode == ThemeMode.DARK
                                }
                                saveSettings()
                            },
                            useSystemColorScheme = useSystemColorScheme,
                            useAmoledTheme = useAmoledTheme,
                            autoSkipSegments = autoSkipSegments,
                            onSystemColorSchemeChange = onSystemColorSchemeChange,
                            onAmoledChange = onAmoledChange,
                            onAutoSkipChange = onAutoSkipChange,
                            onConfigureNotifications = onConfigureNotifications,
                            notificationsAvailable = notificationsAvailable,
                            onCheckForUpdates = onCheckForUpdates,
                            onExportLogs = onExportLogs,
                            onOpenUrl = onOpenUrl,
                            onLibraryChanged = refreshLocalData,
                            libraryEntries = libraryState.visibleEntries,
                            profileData = profileState.data,
                            profileLoading = profileState.isLoading,
                            isEditingProfile = isEditingProfile,
                            editedProfileName = editedProfileName,
                            onProfileNameChange = { editedProfileName = it },
                            onProfileEditClick = {
                                if (editedProfileName.isBlank()) {
                                    editedProfileName = DEFAULT_PROFILE_NAME
                                }
                                isEditingProfile = true
                            },
                            onProfileSaveClick = {
                                val profileName = editedProfileName.trim().ifBlank { DEFAULT_PROFILE_NAME }
                                profileRepository.updateProfileName(profileName)
                                profilePresenter.updateProfileName(profileName)
                                editedProfileName = profileName
                                isEditingProfile = false
                            },
                            onProfileSettingsClick = {
                                selectedTab = AppDestination.SETTINGS
                                if (navigationState.currentRoute !is AppRoute.Settings) {
                                    navigationState = navigationState.reduce(
                                        AppNavigationEvent.Navigate(AppRoute.Settings),
                                    )
                                }
                            },
                            onProfileAvatarEdit = onProfileAvatarEdit,
                            profileAvatarEditAvailable = profileAvatarEditAvailable,
                            onGitHubClick = onGitHubClick,
                            discordEnabled = discordRpcController?.isEnabled() == true,
                            discordAvailable = discordRpcController != null,
                            onDiscordClick = { if (discordRpcController != null) openDiscordAuthDialog() },
                            onDiscordChange = { enabled ->
                                discordRpcController?.let { controller ->
                                    if (enabled && !controller.hasToken()) openDiscordAuthDialog()
                                    else controller.setEnabled(enabled)
                                }
                            },
                            onProfileAvatarPicked = { uri ->
                                profileRepository.updateProfileAvatar(uri)
                                profilePresenter.updateProfileAvatar(uri)
                            },
                            profileRepository = profileRepository,
                            sources = sources,
                            selectedSourceId = currentSelectedSourceId,
                            onSourceSelected = { sourceId ->
                                currentSelectedSourceId = sourceId
                                repository.selectSource(sourceId)
                                presenter.clear()
                                presenter.loadFilterCatalog()
                                presenter.search()
                                sourceSearchPresenter.clear()
                                onSourceSelected(sourceId)
                            },
                            showSettingsBackButton = showSettingsBackButton,
                            includeNavigationBarPadding = includeNavigationBarPadding,
                            onSettingsBack = {
                                navigationState = navigationState.reduce(AppNavigationEvent.Back)
                                selectedTab = AppDestination.PROFILE
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
                    }
                    if (playbackHost != null && (activePlaybackRoute != null || pendingPlaybackContext != null)) {
                        val route = activePlaybackRoute
                        AppPlaybackOverlayHost(
                            playback = route?.playback,
                            context = route?.context ?: requireNotNull(pendingPlaybackContext),
                            navigationState = navigationState,
                            playbackLoading = playerState.isLoading,
                            playbackError = playerState.errorMessage,
                            onRetry = {
                                playerState.lastPlaybackRequest?.let { failedRequest ->
                                    requestPlayback(
                                        episode = failedRequest.episode,
                                        sourceOverride = failedRequest.source,
                                        preferredPlayerName = failedRequest.preferredPlayerName,
                                        preferredQuality = failedRequest.preferredQuality,
                                    )
                                }
                            },
                            onDismiss = {
                                playbackJob?.cancel()
                                playbackJob = null
                                playbackRequestGeneration++
                                activePlaybackRoute = null
                                pendingPlaybackContext = null
                                resetPlayerState()
                                navigationState = navigationState.reduce(AppNavigationEvent.Back)
                            },
                            onEpisodeSelected = { episode ->
                                requestPlayback(
                                    episode = episode,
                                    replacePlayerRoute = true,
                                )
                            },
                            onSettingsAction = ::handlePlaybackSettingsAction,
                            onOverlayEvent = { event -> navigationState = navigationState.reduce(event) },
                            content = { playback, context, navigationState, onDismiss, onEpisodeSelected, onSettingsAction, onOverlayEvent ->
                            playbackHost(playback, context, navigationState, onDismiss, onEpisodeSelected, onSettingsAction, onOverlayEvent)
                            },
                        )
                    }
                    if (enableOnboarding && !onboardingCompleted) {
                        AppOnboardingScreen(
                            sources = sources,
                            initialSourceId = onboardingSourceId,
                            notificationPermissionState = onboardingNotificationPermissionState,
                            onRequestNotificationPermission = onRequestOnboardingNotificationPermission,
                            onComplete = { sourceId ->
                                onboardingSourceId = sourceId
                                currentSelectedSourceId = sourceId
                                onboardingCompleted = true
                                settingsStore.save(
                                    settingsStore.load().copy(
                                        onboardingCompleted = true,
                                        selectedSourceId = sourceId,
                                    ),
                                )
                                repository.selectSource(sourceId)
                                onSourceSelected(sourceId)
                            },
                        )
                    }
                    if (isDiscordAuthDialogOpen && discordRpcController != null) {
                        val controller = discordRpcController
                        AppDiscordAuthDialog(
                            initialToken = pendingDiscordToken ?: controller.tokenForEditing().orEmpty(),
                            isSignedIn = controller.hasToken(),
                            statusText = listOfNotNull(
                                discordRpcState.accountName,
                                resolveDiscordRpcStatusLabel(
                                    status = discordRpcState.status,
                                    disabledLabel = appText(AppTextKey.DiscordStatusDisabled),
                                    signedOutLabel = appText(AppTextKey.DiscordStatusSignedOut),
                                    checkingLabel = appText(AppTextKey.DiscordStatusChecking),
                                    connectingLabel = appText(AppTextKey.DiscordStatusConnecting),
                                    connectedLabel = appText(AppTextKey.DiscordStatusConnected),
                                    errorLabel = appText(AppTextKey.DiscordStatusError),
                                ),
                            ).distinct().joinToString(" · "),
                            isChecking = discordRpcState.status.isBusy(),
                            iconContent = { iconModifier ->
                                androidx.compose.foundation.Image(
                                    painter = org.jetbrains.compose.resources.painterResource(Res.drawable.ic_discord),
                                    contentDescription = null,
                                    modifier = iconModifier,
                                )
                            },
                            title = appText(AppTextKey.SettingsDiscord),
                            manualTokenLabel = appText(AppTextKey.DiscordManualToken),
                            invalidTokenLabel = appText(AppTextKey.DiscordInvalidToken),
                            disconnectLabel = appText(AppTextKey.DiscordDisconnect),
                            browserSignInLabel = appText(AppTextKey.DiscordBrowserSignIn),
                            cancelLabel = appText(AppTextKey.Cancel),
                            applyLabel = appText(AppTextKey.Apply),
                            onBrowserSignIn = { onDiscordBrowserSignIn { token ->
                                pendingDiscordToken = token
                                openDiscordAuthDialog()
                            } },
                            onDisconnect = {
                                controller.signOut()
                                closeDiscordAuthDialog()
                            },
                            onDismiss = {
                                pendingDiscordToken = null
                                closeDiscordAuthDialog()
                            },
                            onAuthenticate = controller::authenticate,
                        )
                    }
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
    watchAnime: Anime?,
    onAnimeClick: (Anime) -> Unit,
    onBackFromDetails: () -> Unit,
    onWatchClick: (Anime) -> Unit,
    onBackFromWatch: () -> Unit,
    watchState: WatchSourcesScreenState,
    episodesState: EpisodesScreenState,
    selectedWatchSource: WatchSource?,
    playbackError: String?,
    playbackLoading: Boolean,
    onWatchRetry: () -> Unit,
    onWatchLoadMore: () -> Unit,
    onWatchSourceClick: (org.akkirrai.hibiki.shared.model.WatchSource) -> Unit,
    onWatchEpisodeClick: (org.akkirrai.hibiki.shared.model.WatchEpisode) -> Unit,
    watchRepositoryAvailable: Boolean,
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
    onGitHubClick: () -> Unit,
    onProfileAvatarPicked: (String) -> Unit,
    profileRepository: LocalProfileDataRepository,
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    onSourceSelected: (String) -> Unit,
    sourceSearchState: SourcesSearchUiState,
    onSourceSearchQueryChange: (String) -> Unit,
    onSourceSearchClear: () -> Unit,
    onSourceSearchRetry: () -> Unit,
    onConfigureNotifications: () -> Unit,
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
            watchAnime,
            onAnimeClick,
            onBackFromDetails,
            onWatchClick,
            onBackFromWatch,
            watchState,
            episodesState,
            selectedWatchSource,
            playbackError,
            playbackLoading,
            onWatchRetry,
            onWatchLoadMore,
            onWatchSourceClick,
            onWatchEpisodeClick,
            watchRepositoryAvailable,
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
            onConfigureNotifications = onConfigureNotifications,
            modifier = Modifier.weight(1f),
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
    watchAnime: Anime?,
    onAnimeClick: (Anime) -> Unit,
    onBackFromDetails: () -> Unit,
    onWatchClick: (Anime) -> Unit,
    onBackFromWatch: () -> Unit,
    watchState: WatchSourcesScreenState,
    episodesState: EpisodesScreenState,
    selectedWatchSource: WatchSource?,
    playbackError: String?,
    playbackLoading: Boolean,
    onWatchRetry: () -> Unit,
    onWatchLoadMore: () -> Unit,
    onWatchSourceClick: (org.akkirrai.hibiki.shared.model.WatchSource) -> Unit,
    onWatchEpisodeClick: (org.akkirrai.hibiki.shared.model.WatchEpisode) -> Unit,
    watchRepositoryAvailable: Boolean,
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
    sourceSearchState: SourcesSearchUiState,
    onSourceSearchQueryChange: (String) -> Unit,
    onSourceSearchClear: () -> Unit,
    onSourceSearchRetry: () -> Unit,
    onConfigureNotifications: () -> Unit,
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
            watchAnime,
            onAnimeClick,
            onBackFromDetails,
            onWatchClick,
            onBackFromWatch,
            watchState,
            episodesState,
            selectedWatchSource,
            playbackError,
            playbackLoading,
            onWatchRetry,
            onWatchLoadMore,
            onWatchSourceClick,
            onWatchEpisodeClick,
            watchRepositoryAvailable,
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
            onConfigureNotifications = onConfigureNotifications,
            modifier = Modifier.padding(padding),
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
    watchAnime: Anime?,
    onAnimeClick: (Anime) -> Unit,
    onBackFromDetails: () -> Unit,
    onWatchClick: (Anime) -> Unit,
    onBackFromWatch: () -> Unit,
    watchState: WatchSourcesScreenState,
    episodesState: EpisodesScreenState,
    selectedWatchSource: WatchSource?,
    playbackError: String?,
    playbackLoading: Boolean,
    onWatchRetry: () -> Unit,
    onWatchLoadMore: () -> Unit,
    onWatchSourceClick: (org.akkirrai.hibiki.shared.model.WatchSource) -> Unit,
    onWatchEpisodeClick: (org.akkirrai.hibiki.shared.model.WatchEpisode) -> Unit,
    watchRepositoryAvailable: Boolean,
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
    sourceSearchState: SourcesSearchUiState,
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
    onConfigureNotifications: () -> Unit = {},
    episodeDownloadRepository: EpisodeDownloadRepository? = null,
    offlineWatchDataRepository: OfflineWatchDataRepository? = null,
    offlineTitleMetadataRepository: OfflineTitleMetadataRepository? = null,
    resumeFrameContent: (@Composable (String, Modifier) -> Unit)? = null,
    downloadMode: Boolean = false,
    detailsResumeState: TitleWatchState? = null,
    onLibraryChanged: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.LIGHT,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    showSettingsBackButton: Boolean = false,
    onSettingsBack: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onGitHubClick: () -> Unit = {},
    discordEnabled: Boolean = false,
    discordAvailable: Boolean = true,
    onDiscordClick: () -> Unit = {},
    onDiscordChange: (Boolean) -> Unit = {},
    includeNavigationBarPadding: Boolean = true,
    homeQuery: String = query,
    onHomeQueryChange: (String) -> Unit = onQueryChange,
    profileLoading: Boolean = false,
    profileAvatarEditAvailable: Boolean = false,
    onCheckForUpdates: () -> Unit = {},
    onExportLogs: () -> Unit = {},
    notificationsAvailable: Boolean = true,
    onResumePlayback: (TitleWatchState) -> Unit = {},
) {
    val episodeDownloadSourceId = selectedWatchSource?.sourceId.orEmpty()
    val downloadControlsVisible = rememberEpisodesDownloadControlsVisible(
        sourceId = episodeDownloadSourceId,
        downloadMode = downloadMode,
    )
    var episodeDownloadStates by remember(episodeDownloadSourceId) {
        mutableStateOf<Map<String, EpisodeDownloadState>>(emptyMap())
    }
    val downloadScope = rememberCoroutineScope()
    LaunchedEffect(episodeDownloadRepository, episodeDownloadSourceId, episodesState.result) {
        val repository = episodeDownloadRepository ?: return@LaunchedEffect
        val sourceId = episodeDownloadSourceId.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        val episodes = (episodesState.result as? EpisodesUiState.Content)?.items.orEmpty()
        if (episodes.isEmpty()) return@LaunchedEffect
        while (true) {
            episodeDownloadStates = repository.getEpisodeStates(sourceId, episodes.map(WatchEpisode::id))
            delay(700L)
        }
    }
    if (watchAnime != null) {
        WatchScreenScaffold(
            onBackClick = onBackFromWatch,
            backEnabled = true,
            backContentDescription = appText(AppTextKey.Back),
            modifier = modifier,
        ) { listContentPadding ->
            if (selectedWatchSource == null) {
                AppWatchSourcesContent(
                    state = watchState,
                    emptyTitle = appText(AppTextKey.Watch),
                    emptyMessage = appText(AppTextKey.Watch),
                    retryLabel = appText(AppTextKey.SearchRetry),
                    episodeLabel = appText(AppTextKey.EpisodesShort),
                    loadMoreLabel = appText(AppTextKey.Episodes),
                    enabled = !watchState.isLoading,
                    onRetry = onWatchRetry,
                    onSourceClick = onWatchSourceClick,
                    onLoadMore = onWatchLoadMore,
                    listContentPadding = listContentPadding,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val currentWatchSource = requireNotNull(selectedWatchSource)
                Box(modifier = Modifier.fillMaxSize()) {
                    if (episodeDownloadRepository != null) {
                        AppEpisodesDownloadToggle(
                            isVisible = downloadControlsVisible.value,
                            contentDescription = appText(AppTextKey.WatchDownload),
                            onClick = { downloadControlsVisible.value = !downloadControlsVisible.value },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(
                                    end = EpisodesDownloadToggleEndPadding,
                                    top = EpisodesDownloadToggleTopPadding,
                                ),
                        )
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        AppEpisodesContent(
                        result = episodesState.result,
                        sourceTitle = currentWatchSource.title,
                        emptyMessage = appText(AppTextKey.Episodes),
                        retryLabel = appText(AppTextKey.SearchRetry),
                        onRetry = onWatchRetry,
                            episodeContent = { episode, shape ->
                            val progress = profileData.episodeProgress.firstOrNull { progress ->
                                progress.titleId == watchAnime.id && progress.episodeId == episode.id
                            }
                            val status = resolveEpisodeProgressStatus(progress)
                            val defaultHeadline = appText(AppTextKey.WatchContinueEpisode)
                                .removePrefix("${appText(AppTextKey.WatchContinue)} · ")
                                if (episodeDownloadRepository != null) {
                                    AppEpisodeDownloadRowContent(
                                        episode = episode,
                                        progress = progress,
                                        status = status,
                                        downloadState = episodeDownloadStates[episode.id]
                                            ?.toEpisodeDownloadActionState()
                                            ?: EpisodeDownloadState.NotDownloaded.toEpisodeDownloadActionState(),
                                        showDownloadControls = downloadControlsVisible.value,
                                        shape = shape,
                                        enabled = !playbackLoading,
                                        watchedHeadline = { number -> "✓ $defaultHeadline".replace("%s", number) },
                                        defaultHeadline = { number -> defaultHeadline.replace("%s", number) },
                                        watchedLabel = appText(AppTextKey.ProfileAnalyticsWatched),
                                        queuedLabel = appText(AppTextKey.WatchStatusQueued),
                                        downloadingLabel = { percent ->
                                            appText(AppTextKey.WatchStatusDownloading).replace("%s", percent.toString())
                                        },
                                        pausedLabel = appText(AppTextKey.WatchStatusPaused),
                                        downloadedLabel = appText(AppTextKey.WatchDownloaded),
                                        failedLabel = appText(AppTextKey.WatchStatusFailed),
                                        downloadedContentDescription = appText(AppTextKey.WatchDownloaded),
                                        downloadContentDescription = appText(AppTextKey.WatchDownload),
                                        pauseContentDescription = appText(AppTextKey.WatchPause),
                                        resumeContentDescription = appText(AppTextKey.WatchResume),
                                        removeContentDescription = appText(AppTextKey.WatchRemoveDownload),
                                        onClick = { onWatchEpisodeClick(episode) },
                                        onDownloadClick = {
                                            episodeDownloadStates = episodeDownloadStates +
                                                (episode.id to EpisodeDownloadState.Queued)
                                            downloadScope.launch {
                                                episodeDownloadRepository.enqueueEpisodes(
                                                    currentWatchSource,
                                                    listOf(episode),
                                                )
                                            }
                                        },
                                        onPauseClick = {
                                            episodeDownloadRepository.pauseEpisode(currentWatchSource.sourceId, episode.id)
                                        },
                                        onResumeClick = {
                                            episodeDownloadRepository.resumeEpisode(currentWatchSource.sourceId, episode.id)
                                        },
                                        onRemoveClick = {
                                            episodeDownloadRepository.removeEpisode(currentWatchSource.sourceId, episode.id)
                                            episodeDownloadStates = episodeDownloadStates +
                                                (episode.id to EpisodeDownloadState.NotDownloaded)
                                            if (!episodeDownloadStates.values
                                                    .map(EpisodeDownloadState::toEpisodeDownloadActionState)
                                                    .any(EpisodeDownloadActionState::keepsTitleSaved)
                                            ) {
                                                watchAnime?.id?.let(libraryRepository::removeSavedFromLibrary)
                                            }
                                            onLibraryChanged()
                                        },
                                    )
                                } else {
                                    EpisodeRow(
                                        headline = buildEpisodeRowHeadline(
                                    episode = episode,
                                    progress = progress,
                                    status = status,
                                    watchedHeadline = { number -> "✓ $defaultHeadline".replace("%s", number) },
                                    defaultHeadline = { number -> defaultHeadline.replace("%s", number) },
                                    watchedLabel = appText(AppTextKey.ProfileAnalyticsWatched),
                                ),
                                        subtitle = episode.title,
                                        inProgress = status == org.akkirrai.hibiki.shared.model.EpisodeProgressStatus.InProgress,
                                        enabled = !playbackLoading,
                                        showDownloadAction = false,
                                        shape = shape,
                                        onClick = { onWatchEpisodeClick(episode) },
                                    )
                                }
                        },
                        listContentPadding = listContentPadding,
                        modifier = Modifier.weight(1f),
                        )
                    }
                    AppPlayerLoadingOverlay(visible = playbackLoading)
                    playbackError?.let { message ->
                        AppPlayerErrorOverlay(
                            message = message,
                            title = appText(AppTextKey.PlayerErrorTitle),
                            retryLabel = appText(AppTextKey.PlayerRetry),
                            onRetry = onWatchRetry,
                        )
                    }
                }
            }
        }
        return
    }
    if (selectedAnime != null) {
        AppDetailsScreen(
            anime = selectedAnime,
            onBackClick = onBackFromDetails,
            onRelatedAnimeClick = onAnimeClick,
            backHandler = { onBack ->
                AppSystemBackHandler(enabled = true, onBack = onBack) {}
            },
            canWatch = watchRepositoryAvailable,
            onWatchClick = { onWatchClick(selectedAnime) },
            onTrailerClick = selectedAnime.trailer?.playbackUrl?.let { url -> { onOpenUrl(url) } },
            resumeState = detailsResumeState,
            onResumeClick = onResumePlayback,
            resumeFrameContent = detailsResumeState?.let { state ->
                resumeFrameContent?.let { content ->
                    { frameModifier -> content(state.titleId, frameModifier) }
                }
            },
            libraryRepository = libraryRepository,
            onLibraryCategoryChange = { onLibraryChanged() },
            modifier = modifier.fillMaxSize(),
            isDetailsLoading = isDetailsLoading,
            detailsError = detailsError,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        when (selectedTab) {
                AppDestination.HOME -> HomeScreen(
                    state = catalogState,
                    listState = catalogListState,
                    libraryStatusByAnimeId = libraryEntries.associate { it.anime.id to it.category },
                    libraryEntries = libraryEntries,
                    query = homeQuery,
                    onQueryChange = onHomeQueryChange,
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
                    sources = sources,
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
                    val profileDateTodayLabel = appText(AppTextKey.ProfileDateToday)
                    val profileDateYesterdayLabel = appText(AppTextKey.ProfileDateYesterday)
                    val profileDateDaysAgoTemplate = appText(AppTextKey.ProfileDateDaysAgo)
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
                        activityDateStrings = defaultProfileActivityDateStrings(),
                        labels = LocalProfileSnapshotLabels(
                            durationLabel = { duration -> "${formatDurationHours(duration)} h" },
                            categoryLabel = { category -> categoryLabels.getValue(category) },
                            dateLabel = { value ->
                                profileRecentDateLabel(
                                    value = value,
                                    languageTag = resolveAppLanguageTag(languageMode, systemLanguage),
                                    todayLabel = profileDateTodayLabel,
                                    yesterdayLabel = profileDateYesterdayLabel,
                                    daysAgoLabel = { days ->
                                        profileDateDaysAgoTemplate.replace("%d", days.toString())
                                    },
                                )
                            },
                            activityDateLabel = ::profileActivityDateLabel,
                        ),
                    )
                    AppLocalProfileScreen(
                        snapshot = snapshot,
                        profileName = profileData.profileName.ifBlank { appText(AppTextKey.AppName) },
                        isLoading = profileLoading,
                        avatarEditAvailable = profileAvatarEditAvailable,
                        isEditing = isEditingProfile,
                        editedName = editedProfileName,
                        bottomContentPadding = AppBottomBarHeight + AppBottomBarContentExtraPadding,
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
                            "ru", "русский" -> "RU"
                            "en", "english" -> "EN"
                            else -> language.uppercase()
                        }
                    },
                    onSourceSelected = onSourceSelected,
                    searchQuery = sourceSearchState.query,
                    searchItems = emptyList(),
                    isSearchLoading = sourceSearchState.isSearching,
                    searchError = sourceSearchState.sections.any { it.hasError },
                    searchSourceId = "",
                    searchSourceName = "",
                    onSearchQueryChange = onSourceSearchQueryChange,
                    onSearchClear = onSourceSearchClear,
                    searchPlaceholder = appText(AppTextKey.SearchPlaceholder),
                    searchErrorLabel = appText(AppTextKey.Unknown),
                    searchRetryLabel = appText(AppTextKey.Search),
                    searchEmptyTitle = appText(AppTextKey.Sources),
                    announcementLabel = appText(AppTextKey.Announcement),
                    movieLabel = appText(AppTextKey.Movie),
                    onSearchRetry = onSourceSearchRetry,
                    onAnimeClick = onAnimeClick,
                    searchSections = sourceSearchState.sections,
                    searchSourceIconContent = { section, iconModifier ->
                        AppSourceIconImage(
                            url = sources.firstOrNull { it.id == section.sourceId }?.iconUrl,
                            sourceId = section.sourceId,
                            modifier = iconModifier,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                AppDestination.SETTINGS -> SettingsScreen(
                    profileData = profileData,
                    languageMode = languageMode,
                    onLanguageModeChange = onLanguageModeChange,
                    darkTheme = darkTheme,
                    onThemeChange = onThemeChange,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    versionName = appVersionName,
                    useSystemColorScheme = useSystemColorScheme,
                    useAmoledTheme = useAmoledTheme,
                    autoSkipSegments = autoSkipSegments,
                    onSystemColorSchemeChange = onSystemColorSchemeChange,
                    onAmoledChange = onAmoledChange,
                    onAutoSkipChange = onAutoSkipChange,
                    onConfigureNotifications = onConfigureNotifications,
                    showBackButton = showSettingsBackButton,
                    onBackClick = onSettingsBack,
                    onGitHubClick = onGitHubClick,
                    discordEnabled = discordEnabled,
                    discordAvailable = discordAvailable,
                    onDiscordClick = onDiscordClick,
                    onDiscordChange = onDiscordChange,
                    onCheckForUpdates = onCheckForUpdates,
                    onExportLogs = onExportLogs,
                    notificationsAvailable = notificationsAvailable,
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
        resultsCountLabel = ::appSearchResultsCount,
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
        query.trim().length < HOME_SEARCH_MIN_QUERY_LENGTH -> SearchUiState.Idle
        state.query.trim() != query.trim() -> SearchUiState.Loading
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
        bottomContentPadding = AppBottomBarHeight + AppBottomBarContentExtraPadding,
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
        bottomContentPadding = AppBottomBarHeight + AppBottomBarContentExtraPadding,
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
    sources: List<AppSourceDescriptor>,
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
    val sourcesById = remember(sources) { sources.associateBy(AppSourceDescriptor::id) }
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
                sourceBadgeContent = { titleId ->
                    AnimeKey.parse(titleId)?.sourceId?.value
                        ?.let(sourcesById::get)
                        ?.let { source ->
                            AppSourceBadge(
                                title = source.name,
                                iconContent = { iconModifier ->
                                    AppSourceIconImage(
                                        url = source.iconUrl,
                                        sourceId = source.id,
                                        modifier = iconModifier,
                                    )
                                },
                            )
                        }
                },
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
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    versionName: String,
    useSystemColorScheme: Boolean,
    useAmoledTheme: Boolean,
    autoSkipSegments: Boolean,
    onSystemColorSchemeChange: (Boolean) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onAutoSkipChange: (Boolean) -> Unit,
    onConfigureNotifications: () -> Unit,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    onGitHubClick: () -> Unit = {},
    discordEnabled: Boolean = false,
    discordAvailable: Boolean = true,
    onDiscordClick: () -> Unit = {},
    onDiscordChange: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onExportLogs: () -> Unit = {},
    notificationsAvailable: Boolean = true,
) {
    AppSettingsScreen(
        languageMode = languageMode,
        darkTheme = darkTheme,
        themeMode = themeMode,
        useSystemColorScheme = useSystemColorScheme,
        useAmoledTheme = useAmoledTheme,
        autoSkipSegments = autoSkipSegments,
        discordEnabled = discordEnabled,
        discordAvailable = discordAvailable,
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
        onThemeModeChange = onThemeModeChange,
        onSystemColorSchemeChange = onSystemColorSchemeChange,
        onAmoledChange = onAmoledChange,
        onAutoSkipChange = onAutoSkipChange,
        onDiscordClick = onDiscordClick,
        onDiscordChange = onDiscordChange,
        onCheckForUpdates = onCheckForUpdates,
        onExportLogs = onExportLogs,
        notificationsAvailable = notificationsAvailable,
        onNotificationsClick = onConfigureNotifications,
        onGitHubClick = onGitHubClick,
        modifier = Modifier.fillMaxSize(),
        showBackButton = showBackButton,
        onBackClick = onBackClick,
        backContentDescription = appText(AppTextKey.Back),
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
