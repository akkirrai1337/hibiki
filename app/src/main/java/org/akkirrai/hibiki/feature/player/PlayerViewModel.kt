package org.akkirrai.hibiki.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.model.PlaybackStream
import org.akkirrai.hibiki.core.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.source.AnimeWatchRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.akkirrai.hibiki.core.source.watchTitleIdFromSourceId

class PlayerViewModel(
    sourceId: String,
    episodeId: String,
    initialEpisodeNumber: Double?,
    private val repository: AnimeWatchRepository,
    private val watchStateRepository: WatchStateRepository,
    private val offlineDownloadRepository: OfflineDownloadRepository,
    private val offlineTitleMetadataRepository: OfflineTitleMetadataRepository,
) : ViewModel() {
    private val titleId = watchTitleIdFromSourceId(sourceId)
    private var loadJob: Job? = null
    private var settingsLoadJob: Job? = null
    @Volatile
    private var settingsLoadingKey: String? = null
    private val savedSelection = watchStateRepository.getSelectedSource(titleId)
        .takeIf { it.sourceId == sourceId }
    private val _uiState = MutableStateFlow(
        PlayerUiState(
            currentSourceId = sourceId,
            currentEpisodeId = episodeId,
            currentEpisodeNumber = initialEpisodeNumber,
            selectedPlayerName = watchStateRepository.getSelectedPlayer(titleId, sourceId),
            selectedQualityLabel = savedSelection?.quality,
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        restoreSavedSeek()
        load()
    }

    fun load(
        forceRefresh: Boolean = false,
        excludedStreamUrls: Set<String> = emptySet(),
    ) {
        loadJob?.cancel()
        val state = _uiState.value
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                failedStreamUrls = it.failedStreamUrls + excludedStreamUrls,
                // A caller passing excludedStreamUrls is an automatic recovery attempt (see
                // recoverFromPlaybackError below) - count it against the per-episode cap so a
                // stream that resolves "successfully" but then reliably fails to actually play
                // (e.g. a CORS-blocked relay CDN) can't retry itself forever. Any other caller
                // (selecting a different episode/player/quality) is a fresh, user-initiated
                // attempt and resets the count.
                autoRecoveryCount = if (excludedStreamUrls.isNotEmpty()) it.autoRecoveryCount + 1 else 0,
            )
        }
        loadSettingsOptions()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val episodesResult = runCatching {
                offlineDownloadRepository.getOfflineEpisodes(state.currentSourceId)
                    .takeIf { it.isNotEmpty() }
                    ?: repository.getEpisodes(state.currentSourceId)
            }.throwIfCancelled()
            val currentState = _uiState.value
            if (currentState.currentSourceId != state.currentSourceId || currentState.currentEpisodeId != state.currentEpisodeId) {
                return@launch
            }
            val episodes = episodesResult.getOrDefault(currentState.episodes)
            val effectiveEpisode = resolveCurrentEpisode(
                requestedEpisodeId = state.currentEpisodeId,
                requestedEpisodeNumber = state.currentEpisodeNumber,
                episodes = episodes,
                currentEpisodes = currentState.episodes,
            )
            val effectiveEpisodeId = effectiveEpisode?.id ?: state.currentEpisodeId
            val effectiveEpisodeNumber = effectiveEpisode?.number ?: state.currentEpisodeNumber
            val offlinePlayback = offlineDownloadRepository.getOfflinePlayback(
                    sourceId = state.currentSourceId,
                    episodeId = effectiveEpisodeId,
                )
            // Every stream this episode has already failed on, not only the one that failed last:
            // a second attempt that remembers only the newest failure can resolve straight back to
            // the first one, and the retry cap then spends itself alternating between two dead
            // candidates.
            val unplayable = _uiState.value.failedStreamUrls + excludedStreamUrls
            val offlineCandidate = offlinePlayback?.takeIf { it.streamUrl !in unplayable }
            val playbackResult = runCatching {
                offlineCandidate
                    ?: if (state.selectedPlayerName.isNullOrBlank() && state.selectedQualityLabel.isNullOrBlank()) {
                        repository.resolveFastestStream(
                            sourceId = state.currentSourceId,
                            episodeId = effectiveEpisodeId,
                            forceRefresh = forceRefresh,
                            excludedStreamUrls = unplayable,
                        ).playback
                    } else {
                        repository.resolveStream(
                            sourceId = state.currentSourceId,
                            episodeId = effectiveEpisodeId,
                            forceRefresh = forceRefresh,
                            excludedStreamUrls = unplayable,
                            preferredPlayerName = state.selectedPlayerName,
                            preferredQuality = state.selectedQualityLabel,
                        )
                    }
            }.throwIfCancelled()
            currentCoroutineContext().ensureActive()

            playbackResult
                .onSuccess { resolvedStream ->
                    val stream = if (offlineCandidate != null) {
                        offlineTitleMetadataRepository.get(titleId)?.title
                            ?.takeIf(String::isNotBlank)
                            ?.let { resolvedStream.copy(animeTitle = it) }
                            ?: resolvedStream
                    } else {
                        resolvedStream
                    }
                    val savedSeekMs = findSavedSeekMs(
                        episodeId = effectiveEpisodeId,
                        episodes = episodes,
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            playback = stream,
                            isPlayingOffline = offlineCandidate != null,
                            animeTitle = stream.animeTitle.trim().takeIf(String::isNotBlank)
                                ?: it.animeTitle,
                            errorMessage = null,
                            offlinePlaybackFailed = false,
                            episodes = episodes,
                            currentEpisodeId = effectiveEpisodeId,
                            currentEpisodeNumber = effectiveEpisodeNumber,
                            pendingSeekMs = savedSeekMs ?: it.pendingSeekMs,
                            failedStreamUrls = it.failedStreamUrls - stream.streamUrl,
                            selectedQualityLabel = stream.qualityLabel ?: it.selectedQualityLabel,
                            availableQualityLabels = stream.availableQualityLabels,
                        )
                    }
                    loadSettingsOptions()
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) return@onFailure
                    AppLogger.e(
                        PLAYBACK_LOG_TAG,
                        "[viewmodel.load.fail] sourceId=${state.currentSourceId} episodeId=${state.currentEpisodeId} error=${throwable.javaClass.simpleName}:${throwable.message}",
                        throwable
                    )
                    // The episode is downloaded and it was the local file that just failed, so a
                    // failed fallback resolution says nothing about the connection - the file is on
                    // the device either way. Reporting the network message here is what made a
                    // broken download look like "no internet"; the screen shows a downloaded-file
                    // error and offers a re-download for this case instead.
                    val downloadedFileOffline = offlinePlayback != null &&
                        offlinePlayback.streamUrl in unplayable &&
                        throwable is NoInternetConnectionException
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            playback = null,
                            isPlayingOffline = false,
                            errorMessage = if (downloadedFileOffline) null else throwable.toUiMessage(),
                            offlinePlaybackFailed = downloadedFileOffline,
                            episodes = episodes,
                            currentEpisodeId = effectiveEpisodeId,
                            currentEpisodeNumber = effectiveEpisodeNumber,
                        )
                    }
                }
        }
    }

    fun selectEpisode(
        episodeId: String,
        resumePositionMs: Long = 0L,
        episodeNumberHint: Double? = null,
    ) {
        if (episodeId == _uiState.value.currentEpisodeId && _uiState.value.playback != null) {
            return
        }
        settingsLoadJob?.cancel()
        settingsLoadingKey = null
        _uiState.update { currentState ->
            currentState.copy(
                currentEpisodeId = episodeId,
                currentEpisodeNumber = episodeNumberHint
                    ?: currentState.episodes.firstOrNull { it.id == episodeId }?.number
                    ?: currentState.currentEpisodeNumber,
                playback = null,
                pendingSeekMs = resumePositionMs.coerceAtLeast(0L),
                settingsOptionsKey = null,
                isSettingsLoading = false,
                failedStreamUrls = emptySet(),
                autoRecoveryCount = 0,
            )
        }
        load()
    }

    /**
     * Auto-triggered from [Player.Listener.onPlayerError]. A resolved stream can pass the source's
     * own resolution step and still be unplayable for a reason that has nothing to do with which
     * candidate URL was picked (e.g. a relay CDN that's CORS-blocked no matter which mirror is
     * tried) - without a cap, every such failure looks like "try the next candidate" and the player
     * retries forever, stuck on a loading spinner with no visible error. [MAX_AUTO_RECOVERY_ATTEMPTS]
     * bounds that; past it, a real error is surfaced instead of another silent retry.
     */
    fun recoverFromPlaybackError(streamUrl: String?) {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.autoRecoveryCount >= MAX_AUTO_RECOVERY_ATTEMPTS) {
            _uiState.update {
                it.copy(errorMessage = "Не удалось воспроизвести поток после нескольких попыток")
            }
            return
        }
        val excluded = streamUrl?.takeIf(String::isNotBlank)?.let { setOf(it) } ?: emptySet()
        // Deliberately not a forced refresh on the first try. A browser-resolved link offers two
        // candidates - the direct CDN URL and the same stream relayed through the WebView that
        // resolved it, which is the one that gets past bot management on TLS fingerprint - and the
        // point of this retry is to reach the second. Forcing a refresh instead throws away the
        // resolution that produced both, discards the relay session behind the second, and can mint
        // a fresh direct URL that the exclusion above no longer matches, so the player fails on the
        // same kind of stream until the cap is spent. A refresh is what a second failure means: the
        // resolution itself has gone stale.
        load(forceRefresh = state.autoRecoveryCount > 0, excludedStreamUrls = excluded)
    }

    /**
     * Re-downloads the episode whose local file could not be played (see
     * [PlayerUiState.offlinePlaybackFailed]). The download queue resolves a fresh stream for the
     * episode, so this needs a connection - offline it reports that, which is the accurate reason
     * rather than a wrong "no internet" for a file that is already on the device.
     */
    fun redownloadCurrentEpisode() {
        val state = _uiState.value
        if (state.isLoading) return
        val episode = state.episodes.firstOrNull { it.id == state.currentEpisodeId } ?: return
        val source = WatchSource(
            sourceId = state.currentSourceId,
            title = savedSelection?.sourceTitle.orEmpty(),
            episodeCount = state.episodes.size,
            qualityLabel = savedSelection?.quality,
        )
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                offlinePlaybackFailed = false,
                failedStreamUrls = emptySet(),
                autoRecoveryCount = 0,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                offlineDownloadRepository.redownloadEpisode(source = source, episode = episode)
            }.onSuccess {
                AppLogger.d(
                    PLAYBACK_LOG_TAG,
                    "[viewmodel.redownload] replacement scheduled sourceId=${source.sourceId} episodeId=${episode.id}",
                )
                _uiState.update { it.copy(isLoading = false) }
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                AppLogger.e(
                    PLAYBACK_LOG_TAG,
                    "[viewmodel.redownload.fail] sourceId=${source.sourceId} episodeId=${episode.id} " +
                        "error=${throwable.javaClass.simpleName}:${throwable.message}",
                    throwable,
                )
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = throwable.toUiMessage())
                }
            }
        }
    }

    fun loadSettingsOptions() {
        val state = _uiState.value
        val optionsKey = state.settingsOptionsKey()
        if (state.settingsOptionsKey == optionsKey ||
            (settingsLoadJob?.isActive == true && settingsLoadingKey == optionsKey)
        ) {
            return
        }
        settingsLoadJob?.cancel()
        settingsLoadingKey = optionsKey
        _uiState.update { it.copy(isSettingsLoading = true) }
        settingsLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                repository.getPlaybackSettingsOptions(
                    sourceId = state.currentSourceId,
                    episodeId = state.currentEpisodeId,
                )
            }.throwIfCancelled()
            currentCoroutineContext().ensureActive()
            val updatedState = _uiState.value
            if (updatedState.currentSourceId != state.currentSourceId || updatedState.currentEpisodeId != state.currentEpisodeId) {
                if (settingsLoadingKey == optionsKey) {
                    settingsLoadingKey = null
                    _uiState.update { it.copy(isSettingsLoading = false) }
                }
                return@launch
            }
            result
                .onSuccess { options ->
                    settingsLoadingKey = null
                    _uiState.update {
                        it.copy(
                            isSettingsLoading = false,
                            settingsOptions = options,
                            settingsOptionsKey = optionsKey,
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) return@onFailure
                    settingsLoadingKey = null
                    AppLogger.e(
                        PLAYBACK_LOG_TAG,
                        "[viewmodel.settings.fail] sourceId=${state.currentSourceId} episodeId=${state.currentEpisodeId} error=${throwable.javaClass.simpleName}:${throwable.message}",
                        throwable
                    )
                    _uiState.update {
                        it.copy(isSettingsLoading = false)
                    }
                }
        }
    }

    fun selectVoiceover(source: WatchSource, resumePositionMs: Long = 0L) {
        val currentEpisode = _uiState.value.episodes.firstOrNull { it.id == _uiState.value.currentEpisodeId } ?: return
        settingsLoadJob?.cancel()
        settingsLoadingKey = null
        viewModelScope.launch(Dispatchers.IO) {
            val episodes = repository.getEpisodes(source.sourceId)
            val matching = episodes.firstOrNull { it.number == currentEpisode.number } ?: episodes.firstOrNull() ?: return@launch
            val rememberedPlayer = watchStateRepository.getSelectedPlayer(titleId, source.sourceId)
            _uiState.update {
                it.copy(
                    currentSourceId = source.sourceId,
                    currentEpisodeId = matching.id,
                    episodes = episodes,
                    selectedPlayerName = rememberedPlayer,
                    selectedQualityLabel = source.qualityLabel,
                    pendingSeekMs = resumePositionMs.coerceAtLeast(0L),
                    settingsOptionsKey = null,
                    isSettingsLoading = false,
                    failedStreamUrls = emptySet(),
                    autoRecoveryCount = 0,
                )
            }
            watchStateRepository.saveSelectedSource(
                titleId = titleId,
                sourceId = source.sourceId,
                sourceTitle = source.title,
                quality = source.qualityLabel,
                playerName = rememberedPlayer,
                autoSelect = false,
            )
            restoreSavedSeek()
            load()
        }
    }

    fun selectPlayer(playerName: String?, resumePositionMs: Long = 0L) {
        _uiState.update {
            it.copy(
                selectedPlayerName = playerName,
                playback = null,
                pendingSeekMs = resumePositionMs.coerceAtLeast(0L),
                failedStreamUrls = emptySet(),
                autoRecoveryCount = 0,
            )
        }
        persistSelection()
        load()
    }

    fun selectQuality(qualityLabel: String?, resumePositionMs: Long = 0L) {
        _uiState.update {
            it.copy(
                selectedQualityLabel = qualityLabel,
                playback = null,
                pendingSeekMs = resumePositionMs.coerceAtLeast(0L),
                failedStreamUrls = emptySet(),
                autoRecoveryCount = 0,
            )
        }
        persistSelection()
        load()
    }

    fun savePlaybackProgress(
        positionMs: Long,
        durationMs: Long,
        watchedSeconds: List<Long> = emptyList(),
    ) {
        val safePositionMs = positionMs.coerceAtLeast(0L)
        if (safePositionMs <= 0L) {
            return
        }
        // ExoPlayer may not know the duration yet. Keep that as 0 so the
        // repository can still resume from the saved position later.
        val safeDurationMs = durationMs.coerceAtLeast(0L)
        val state = _uiState.value
        val playback = state.playback ?: return
        val episode = state.episodes.firstOrNull { it.id == state.currentEpisodeId } ?: return
        // The player hands over every second it has seen play for this episode, cumulatively, so
        // what is new since the last save is the difference. Seeks never enter that set, which is
        // the entire point: skipping to the end of a film is not an hour and a half of watching.
        if (reportedWatchedSecondsEpisodeId != episode.id) {
            reportedWatchedSecondsEpisodeId = episode.id
            reportedWatchedSeconds = 0
        }
        val newWatchedSeconds = (watchedSeconds.size - reportedWatchedSeconds).coerceAtLeast(0)
        reportedWatchedSeconds = watchedSeconds.size
        watchStateRepository.saveSelectedSource(
            titleId = titleId,
            sourceId = state.currentSourceId,
            sourceTitle = playback.sourceTitle,
            quality = playback.qualityLabel,
            playerName = state.selectedPlayerName,
            autoSelect = false,
        )
        watchStateRepository.saveEpisodeProgress(
            titleId = titleId,
            episodeId = episode.id,
            episodeNumber = episode.number,
            sourceId = state.currentSourceId,
            voiceoverId = state.currentSourceId,
            sourceTitle = playback.sourceTitle,
            quality = playback.qualityLabel,
            positionMs = safePositionMs,
            durationMs = safeDurationMs,
            watchedMsDelta = newWatchedSeconds * 1_000L,
        )
    }

    /** Which episode [reportedWatchedSeconds] belongs to - the player's set restarts per episode. */
    private var reportedWatchedSecondsEpisodeId: String? = null
    private var reportedWatchedSeconds = 0

    private fun persistSelection() {
        val state = _uiState.value
        val previousSelection = watchStateRepository.getSelectedSource(titleId)
        watchStateRepository.saveSelectedPlayer(
            titleId = titleId,
            sourceId = state.currentSourceId,
            playerName = state.selectedPlayerName,
        )
        watchStateRepository.saveSelectedSource(
            titleId = titleId,
            sourceId = state.currentSourceId,
            sourceTitle = state.playback?.sourceTitle ?: previousSelection.sourceTitle,
            quality = state.selectedQualityLabel ?: state.playback?.qualityLabel,
            playerName = state.selectedPlayerName,
            autoSelect = false,
        )
    }

    fun consumePendingSeek() {
        _uiState.update { it.copy(pendingSeekMs = 0L) }
    }

    fun playPreviousEpisode() {
        adjacentEpisode(offset = -1)?.let { selectEpisode(it.id, episodeNumberHint = it.number) }
    }

    fun playNextEpisode() {
        adjacentEpisode(offset = 1)?.let { selectEpisode(it.id, episodeNumberHint = it.number) }
    }

    override fun onCleared() {
        loadJob?.cancel()
        settingsLoadJob?.cancel()
        repository.close()
        super.onCleared()
    }

    private fun restoreSavedSeek() {
        val state = _uiState.value
        val savedSeekMs = findSavedSeekMs(
            episodeId = state.currentEpisodeId,
            episodes = state.episodes,
        ) ?: return
        _uiState.update { it.copy(pendingSeekMs = savedSeekMs) }
    }

    private fun findSavedSeekMs(
        episodeId: String,
        episodes: List<WatchEpisode>,
    ): Long? {
        val state = _uiState.value
        val exactProgress = watchStateRepository.getEpisodeProgress(
            titleId = titleId,
            episodeId = episodeId,
            sourceId = state.currentSourceId,
        )
        val episodeNumber = episodes.firstOrNull { it.id == episodeId }?.number
        val numberProgress = episodeNumber?.let { number ->
            watchStateRepository.getEpisodeProgress(titleId)
                .filter { it.sourceId == state.currentSourceId && it.episodeNumber == number }
                .maxByOrNull { it.updatedAt }
        }
        val progress = exactProgress ?: numberProgress ?: return null
        return resumablePlaybackPositionMs(progress.positionMs, progress.durationMs)
    }

    private fun adjacentEpisode(offset: Int): WatchEpisode? {
        val state = _uiState.value
        val currentIndex = state.episodes.indexOfFirst { it.id == state.currentEpisodeId }
        if (currentIndex != -1) {
            return state.episodes.getOrNull(currentIndex + offset)
        }
        val currentEpisodeNumber = state.currentEpisodeNumber ?: return null
        return if (offset < 0) {
            state.episodes
                .filter { it.number < currentEpisodeNumber }
                .maxByOrNull { it.number }
        } else {
            state.episodes
                .filter { it.number > currentEpisodeNumber }
                .minByOrNull { it.number }
        }
    }

    private fun resolveCurrentEpisode(
        requestedEpisodeId: String,
        requestedEpisodeNumber: Double?,
        episodes: List<WatchEpisode>,
        currentEpisodes: List<WatchEpisode>,
    ): WatchEpisode? {
        episodes.firstOrNull { it.id == requestedEpisodeId }?.let { return it }

        val knownEpisodeNumber = requestedEpisodeNumber
            ?: currentEpisodes.firstOrNull { it.id == requestedEpisodeId }?.number
            ?: watchStateRepository.getEpisodeProgress(
                titleId = titleId,
                episodeId = requestedEpisodeId,
                sourceId = _uiState.value.currentSourceId,
            )?.episodeNumber

        return knownEpisodeNumber?.let { episodeNumber ->
            episodes.firstOrNull { it.number == episodeNumber }
        }
    }

    class Factory(
        private val sourceId: String,
        private val episodeId: String,
        private val initialEpisodeNumber: Double?,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dependencies = appContext.applicationContext.hibikiDependencies()
            return PlayerViewModel(
                sourceId = sourceId,
                episodeId = episodeId,
                initialEpisodeNumber = initialEpisodeNumber,
                repository = dependencies.animeWatchRepository(),
                watchStateRepository = dependencies.watchStateRepository(),
                offlineDownloadRepository = dependencies.offlineDownloadRepository(),
                offlineTitleMetadataRepository = dependencies.offlineTitleMetadataRepository(),
            ) as T
        }
    }
}

internal fun resumablePlaybackPositionMs(positionMs: Long, durationMs: Long): Long? {
    val position = positionMs.coerceAtLeast(0L).takeIf { it > 0L } ?: return null
    if (durationMs <= 0L) return position
    val duration = durationMs.coerceAtLeast(1L)
    val resetThresholdMs = maxOf(PLAYBACK_END_WINDOW_MS, duration * PLAYBACK_END_PERCENT / 100L)
    return position.takeIf { duration - position > resetThresholdMs }
}

data class PlayerUiState(
    val isLoading: Boolean = true,
    val playback: PlaybackStream? = null,
    /**
     * [playback] came from the local download cache. The screen builds a local-only data source for
     * it so a downloaded episode never falls through to the network (see
     * [org.akkirrai.hibiki.core.download.OfflineMediaCache.buildDownloadedPlaybackDataSourceFactory]).
     */
    val isPlayingOffline: Boolean = false,
    val animeTitle: String = "",
    val episodes: List<WatchEpisode> = emptyList(),
    val currentSourceId: String = "",
    val currentEpisodeId: String = "",
    val currentEpisodeNumber: Double? = null,
    val pendingSeekMs: Long = 0L,
    val errorMessage: String? = null,
    /**
     * The episode's local (downloaded) file failed to play and the only thing the fallback
     * resolution could report was the missing connection. The screen renders a downloaded-file
     * error with a re-download action for this state instead of the network message.
     */
    val offlinePlaybackFailed: Boolean = false,
    val failedStreamUrls: Set<String> = emptySet(),
    val autoRecoveryCount: Int = 0,
    val isSettingsLoading: Boolean = false,
    val settingsOptions: PlaybackSettingsOptions = PlaybackSettingsOptions(),
    val settingsOptionsKey: String? = null,
    val selectedPlayerName: String? = null,
    val selectedQualityLabel: String? = null,
    val availableQualityLabels: List<String> = emptyList(),
)

private fun PlayerUiState.settingsOptionsKey(): String =
    buildString {
        append(currentSourceId)
        append(':')
        append(currentEpisodeId)
    }

private fun <T> Result<T>.throwIfCancelled(): Result<T> {
    val error = exceptionOrNull()
    if (error is CancellationException) throw error
    return this
}

private const val PLAYBACK_LOG_TAG = "HibikiPlayback"
private const val PLAYBACK_END_WINDOW_MS = 30_000L
private const val PLAYBACK_END_PERCENT = 5L
// Enough for the two candidates a browser-resolved link offers - the direct URL and the relayed
// one - plus a move to another player after both fail.
private const val MAX_AUTO_RECOVERY_ATTEMPTS = 3
