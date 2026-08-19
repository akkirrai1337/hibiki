package org.akkirrai.hibiki.app.shell.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.details.data.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.player.model.PlaybackContext
import org.akkirrai.hibiki.player.model.PlaybackSelection
import org.akkirrai.hibiki.player.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.player.model.PlaybackStream
import org.akkirrai.hibiki.player.model.TitleWatchState
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.app.navigation.AppNavigationState
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.app.navigation.currentRoute
import org.akkirrai.hibiki.app.navigation.navigateToPlayer
import org.akkirrai.hibiki.player.EpisodesUiState
import org.akkirrai.hibiki.player.EpisodesScreenState
import org.akkirrai.hibiki.player.HibikiPlaybackSession
import org.akkirrai.hibiki.player.OfflineWatchDataRepository
import org.akkirrai.hibiki.player.PlaybackRequest
import org.akkirrai.hibiki.player.PlaybackSettingsAction
import org.akkirrai.hibiki.player.PlayerPresenter
import org.akkirrai.hibiki.player.WatchDataRepository
import org.akkirrai.hibiki.player.createPlaybackSelection
import org.akkirrai.hibiki.player.beginPlaybackLoad
import org.akkirrai.hibiki.player.handlePlaybackSettingsAction
import org.akkirrai.hibiki.player.launchHibikiPlaybackJob
import org.akkirrai.hibiki.player.preparePlaybackRequest
import org.akkirrai.hibiki.profile.PlaybackProgressRepository

internal class HibikiPlaybackRequestCoordinator(
    private val scope: CoroutineScope,
    private val watchRepository: () -> WatchDataRepository?,
    private val offlineWatchDataRepository: OfflineWatchDataRepository?,
    private val offlineTitleMetadataRepository: OfflineTitleMetadataRepository?,
    private val progressRepository: PlaybackProgressRepository?,
    private val playerPresenter: PlayerPresenter,
    private val playbackSession: HibikiPlaybackSession,
    private val navigationState: () -> AppNavigationState,
    private val setNavigationState: (AppNavigationState) -> Unit,
    private val selectedWatchSource: () -> WatchSource?,
    private val watchAnime: () -> org.akkirrai.hibiki.catalog.model.Anime?,
    private val episodesState: () -> EpisodesScreenState,
    private val activePlaybackRoute: () -> org.akkirrai.hibiki.player.model.PlaybackRoute?,
    private val loadPlaybackSelection: (String) -> PlaybackSelection?,
    private val onPlaybackSelectionChanged: (PlaybackSelection) -> Unit,
    private val onPlaybackReady: (PlaybackStream, PlaybackContext) -> Unit,
    private val playbackHost: (() -> Boolean),
    private val onPendingPlaybackContextChanged: (PlaybackContext?) -> Unit,
    private val onPlaybackPublished: ((PlaybackStream, PlaybackContext) -> Unit)?,
    private val setPlaybackJob: (Job?) -> Unit,
    private val onSourceSelected: (String, WatchSource) -> Unit,
    private val setAutoSkipSegments: (Boolean) -> Unit,
    private val setAutoPlayNextEpisode: (Boolean) -> Unit,
) {
    fun request(
        episode: WatchEpisode,
        sourceOverride: WatchSource? = null,
        preferredPlayerName: String? = null,
        preferredQuality: String? = null,
        forceRefresh: Boolean = false,
        episodesOverride: List<WatchEpisode>? = null,
        replacePlayerRoute: Boolean = false,
        // Only consulted when selectedWatchSource() itself is empty (e.g. the player was opened
        // straight from Home/Catalog rather than via the Episodes tab, so that screen's own
        // source-selection state was never populated) -- unlike sourceOverride, this never
        // suppresses the saved player/quality selection below.
        fallbackSource: WatchSource? = null,
    ) {
        AppLogger.d(PLAYBACK_REQUEST_TAG, "request: episodeId=${episode.id} sourceOverride=${sourceOverride?.sourceId} forceRefresh=$forceRefresh")
        val repository = watchRepository() ?: run {
            AppLogger.w(PLAYBACK_REQUEST_TAG, "request: aborted, watchRepository() is null")
            return
        }
        val requestEpisodes = episodesOverride
            ?: (episodesState().result as? EpisodesUiState.Content)?.items.orEmpty()
        val selectedSource = selectedWatchSource() ?: fallbackSource
        val titleId = watchAnime()?.id.orEmpty()
        val retainedSettingsOptions = activePlaybackRoute()
            ?.takeIf { route -> (sourceOverride ?: selectedSource)?.sourceId == route.context.sourceId }
            ?.context
            ?.settingsOptions
            ?: PlaybackSettingsOptions()
        val preparedRequest = preparePlaybackRequest(
            titleId = titleId,
            animeTitle = watchAnime()?.title.orEmpty(),
            episode = episode,
            selectedSource = selectedSource,
            sourceOverride = sourceOverride,
            preferredPlayerName = preferredPlayerName,
            preferredQuality = preferredQuality,
            savedSelection = loadPlaybackSelection(titleId),
            allowSavedSelection = sourceOverride == null &&
                preferredPlayerName == null &&
                preferredQuality == null,
            episodes = requestEpisodes,
            settingsOptions = retainedSettingsOptions,
        ) ?: run {
            AppLogger.w(
                PLAYBACK_REQUEST_TAG,
                "request: aborted, preparePlaybackRequest returned null " +
                    "(selectedSource=${selectedSource?.sourceId}, titleId=$titleId, episodes=${requestEpisodes.size})",
            )
            return
        }

        val source = preparedRequest.source
        val playerName = preparedRequest.playerName
        val quality = preparedRequest.quality
        val requestContext = preparedRequest.context
        val playerRoute = AppRoute.Player(
            sourceId = source.sourceId,
            episodeId = episode.id,
            episodeNumber = episode.number,
        )
        playbackSession.beginRequest(
            returnRoute = navigationState().currentRoute.takeUnless {
                replacePlayerRoute || it is AppRoute.Player
            },
            context = requestContext,
        )
        setNavigationState(
            navigationState().navigateToPlayer(
                sourceId = playerRoute.sourceId,
                episodeId = playerRoute.episodeId,
                episodeNumber = playerRoute.episodeNumber,
            ),
        )

        playbackSession.cancelAndInvalidate()
        val requestGeneration = playbackSession.requestGeneration.value
        AppLogger.d(
            PLAYBACK_REQUEST_TAG,
            "request: launching job sourceId=${source.sourceId} episodeId=${episode.id} generation=$requestGeneration",
        )
        playerPresenter.update {
            it.copy(
                currentSourceId = source.sourceId,
                currentEpisodeId = episode.id,
                currentEpisodeNumber = episode.number,
                lastPlaybackRequest = PlaybackRequest(
                    episode = episode,
                    source = source,
                    preferredPlayerName = playerName,
                    preferredQuality = quality,
                ),
            ).beginPlaybackLoad(emptySet())
        }
        setPlaybackJob(
            launchHibikiPlaybackJob(
                scope = scope,
                repository = repository,
                offlineWatchDataRepository = offlineWatchDataRepository,
                offlineTitleMetadataRepository = offlineTitleMetadataRepository,
                progressRepository = progressRepository,
                source = source,
                episode = episode,
                requestEpisodes = requestEpisodes,
                preferredQuality = quality,
                preferredPlayerName = playerName,
                forceRefresh = forceRefresh,
                requestGeneration = requestGeneration,
                currentRequestGeneration = { playbackSession.requestGeneration.value },
                titleId = titleId,
                playerPresenter = playerPresenter,
                requestContext = requestContext,
                onNavigateToPlayer = { sourceId, episodeId, episodeNumber ->
                    setNavigationState(navigationState().navigateToPlayer(sourceId, episodeId, episodeNumber))
                },
                onPendingPlaybackContextChanged = onPendingPlaybackContextChanged,
                onPlaybackSelectionChanged = onPlaybackSelectionChanged,
                onPlaybackReady = onPlaybackReady,
                onPlaybackPublished = if (playbackHost()) onPlaybackPublished else null,
                onFinished = {
                    AppLogger.d(PLAYBACK_REQUEST_TAG, "request: job finished generation=$requestGeneration")
                    setPlaybackJob(null)
                },
            ),
        )
    }

    fun requestResume(progress: TitleWatchState) {
        if (watchRepository() == null) return
        request(
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
    }

    fun handleSettingsAction(action: PlaybackSettingsAction) {
        val route = activePlaybackRoute() ?: return
        val repository = watchRepository() ?: return
        handlePlaybackSettingsAction(
            action = action,
            route = route,
            repository = repository,
            scope = scope,
            onSourceSelected = onSourceSelected,
            onSelectionChanged = { source, playerName, quality ->
                onPlaybackSelectionChanged(
                    createPlaybackSelection(route.context.titleId, source, quality, playerName),
                )
            },
            onRequestPlayback = { request ->
                this.request(
                    episode = request.episode,
                    sourceOverride = request.source,
                    preferredPlayerName = request.playerName,
                    preferredQuality = request.quality,
                    forceRefresh = true,
                    episodesOverride = request.episodes,
                    replacePlayerRoute = true,
                )
            },
            onAutoSkipChanged = setAutoSkipSegments,
            onAutoPlayNextChanged = setAutoPlayNextEpisode,
        )
    }
}

private const val PLAYBACK_REQUEST_TAG = "BeakoKit/playback-request"
