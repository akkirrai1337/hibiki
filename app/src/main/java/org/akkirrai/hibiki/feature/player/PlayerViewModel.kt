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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.model.PlaybackStream
import org.akkirrai.hibiki.core.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.source.AnimeWatchRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.akkirrai.hibiki.core.source.watchTitleIdFromSourceId
import org.akkirrai.hibiki.shared.player.PlayerPresenter
import org.akkirrai.hibiki.shared.player.PlayerUiState
import org.akkirrai.hibiki.shared.player.resolveAdjacentEpisode
import org.akkirrai.hibiki.shared.player.settingsOptionsKey
import org.akkirrai.hibiki.shared.player.resolveCurrentEpisode
import org.akkirrai.hibiki.shared.player.resolveResumablePlaybackPosition

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
    private val savedSelection = watchStateRepository.getSelectedSource(titleId)
        .takeIf { it.sourceId == sourceId }
    private val presenter = PlayerPresenter(
        PlayerUiState(
            currentSourceId = sourceId,
            currentEpisodeId = episodeId,
            currentEpisodeNumber = initialEpisodeNumber,
            selectedPlayerName = savedSelection?.playerName,
            selectedQualityLabel = savedSelection?.quality,
        )
    )
    val uiState: StateFlow<PlayerUiState> = presenter.state

    init {
        restoreSavedSeek()
        load()
    }

    fun load(
        forceRefresh: Boolean = false,
        excludedStreamUrls: Set<String> = emptySet(),
    ) {
        loadJob?.cancel()
        val state = presenter.state.value
        presenter.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                failedStreamUrls = it.failedStreamUrls + excludedStreamUrls,
                recoveryAttempted = excludedStreamUrls.isNotEmpty(),
            )
        }
        loadSettingsOptions()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val episodesResult = runCatching {
                offlineDownloadRepository.getOfflineEpisodes(state.currentSourceId)
                    .takeIf { it.isNotEmpty() }
                    ?: repository.getEpisodes(state.currentSourceId)
            }.throwIfCancelled()
            val currentState = presenter.state.value
            if (currentState.currentSourceId != state.currentSourceId || currentState.currentEpisodeId != state.currentEpisodeId) {
                return@launch
            }
            val episodes = episodesResult.getOrDefault(currentState.episodes)
            val savedEpisodeNumber = watchStateRepository.getEpisodeProgress(
                titleId,
                state.currentEpisodeId,
            )?.episodeNumber
            val effectiveEpisode = resolveCurrentEpisode(
                requestedEpisodeId = state.currentEpisodeId,
                requestedEpisodeNumber = state.currentEpisodeNumber,
                episodes = episodes,
                currentEpisodes = currentState.episodes,
                savedEpisodeNumber = savedEpisodeNumber,
            )
            val effectiveEpisodeId = effectiveEpisode?.id ?: state.currentEpisodeId
            val effectiveEpisodeNumber = effectiveEpisode?.number ?: state.currentEpisodeNumber
            val offlinePlayback = offlineDownloadRepository.getOfflinePlayback(
                    sourceId = state.currentSourceId,
                    episodeId = effectiveEpisodeId,
                )
            val playbackResult = runCatching {
                offlinePlayback
                    ?.takeIf { it.streamUrl !in excludedStreamUrls }
                    ?: repository.resolveStream(
                        sourceId = state.currentSourceId,
                        episodeId = effectiveEpisodeId,
                        forceRefresh = forceRefresh,
                        excludedStreamUrls = excludedStreamUrls,
                        preferredPlayerName = state.selectedPlayerName,
                        preferredQuality = state.selectedQualityLabel,
                    )
            }.throwIfCancelled()
            currentCoroutineContext().ensureActive()

            playbackResult
                .onSuccess { resolvedStream ->
                    val stream = if (offlinePlayback != null) {
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
                    presenter.update {
                        it.copy(
                            isLoading = false,
                            playback = stream,
                            animeTitle = stream.animeTitle.trim().takeIf(String::isNotBlank)
                                ?: it.animeTitle,
                            errorMessage = null,
                            episodes = episodes,
                            currentEpisodeId = effectiveEpisodeId,
                            currentEpisodeNumber = effectiveEpisodeNumber,
                            pendingSeekMs = savedSeekMs ?: it.pendingSeekMs,
                            failedStreamUrls = it.failedStreamUrls - stream.streamUrl,
                            recoveryAttempted = false,
                            selectedQualityLabel = stream.qualityLabel ?: it.selectedQualityLabel,
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
                    presenter.update {
                        it.copy(
                            isLoading = false,
                            playback = null,
                            errorMessage = throwable.toUiMessage(),
                            episodes = episodes,
                            currentEpisodeId = effectiveEpisodeId,
                            currentEpisodeNumber = effectiveEpisodeNumber,
                            recoveryAttempted = false,
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
        if (episodeId == presenter.state.value.currentEpisodeId && presenter.state.value.playback != null) {
            return
        }
        settingsLoadJob?.cancel()
        presenter.update { currentState ->
            currentState.copy(
                currentEpisodeId = episodeId,
                currentEpisodeNumber = episodeNumberHint
                    ?: currentState.episodes.firstOrNull { it.id == episodeId }?.number
                    ?: currentState.currentEpisodeNumber,
                playback = null,
                pendingSeekMs = resumePositionMs.coerceAtLeast(0L),
                settingsOptionsKey = null,
                failedStreamUrls = emptySet(),
                recoveryAttempted = false,
            )
        }
        load()
    }

    fun recoverFromPlaybackError(streamUrl: String?) {
        val state = presenter.state.value
        if (state.isLoading || state.recoveryAttempted) {
            return
        }
        val excluded = streamUrl?.takeIf(String::isNotBlank)?.let { setOf(it) } ?: emptySet()
        load(forceRefresh = true, excludedStreamUrls = excluded)
    }

    fun loadSettingsOptions() {
        val state = presenter.state.value
        val optionsKey = state.settingsOptionsKey()
        if (state.settingsOptionsKey == optionsKey || state.isSettingsLoading) {
            return
        }
        settingsLoadJob?.cancel()
        presenter.update { it.copy(isSettingsLoading = true) }
        settingsLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                repository.getPlaybackSettingsOptions(
                    sourceId = state.currentSourceId,
                    episodeId = state.currentEpisodeId,
                )
            }.throwIfCancelled()
            currentCoroutineContext().ensureActive()
            val updatedState = presenter.state.value
            if (updatedState.currentSourceId != state.currentSourceId || updatedState.currentEpisodeId != state.currentEpisodeId) {
                return@launch
            }
            result
                .onSuccess { options ->
                    presenter.update {
                        it.copy(
                            isSettingsLoading = false,
                            settingsOptions = options,
                            settingsOptionsKey = optionsKey,
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) return@onFailure
                    AppLogger.e(
                        PLAYBACK_LOG_TAG,
                        "[viewmodel.settings.fail] sourceId=${state.currentSourceId} episodeId=${state.currentEpisodeId} error=${throwable.javaClass.simpleName}:${throwable.message}",
                        throwable
                    )
                    presenter.update {
                        it.copy(isSettingsLoading = false)
                    }
                }
        }
    }

    fun selectVoiceover(source: WatchSource, resumePositionMs: Long = 0L) {
        val currentState = presenter.state.value
        val currentEpisode = currentState.episodes.firstOrNull { it.id == currentState.currentEpisodeId } ?: return
        settingsLoadJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            val episodes = repository.getEpisodes(source.sourceId)
            val matching = episodes.firstOrNull { it.number == currentEpisode.number } ?: episodes.firstOrNull() ?: return@launch
            presenter.update {
                it.copy(
                    currentSourceId = source.sourceId,
                    currentEpisodeId = matching.id,
                    episodes = episodes,
                    selectedPlayerName = null,
                    selectedQualityLabel = source.qualityLabel,
                    pendingSeekMs = resumePositionMs.coerceAtLeast(0L),
                    settingsOptionsKey = null,
                    failedStreamUrls = emptySet(),
                    recoveryAttempted = false,
                )
            }
            watchStateRepository.saveSelectedSource(
                titleId = titleId,
                sourceId = source.sourceId,
                sourceTitle = source.title,
                quality = source.qualityLabel,
                playerName = null,
                autoSelect = false,
            )
            restoreSavedSeek()
            load(forceRefresh = true)
        }
    }

    fun selectPlayer(playerName: String?, resumePositionMs: Long = 0L) {
        settingsLoadJob?.cancel()
        presenter.update {
            it.copy(
                selectedPlayerName = playerName,
                playback = null,
                pendingSeekMs = resumePositionMs.coerceAtLeast(0L),
                settingsOptionsKey = null,
                failedStreamUrls = emptySet(),
                recoveryAttempted = false,
            )
        }
        persistSelection()
        load(forceRefresh = true)
    }

    fun selectQuality(qualityLabel: String?, resumePositionMs: Long = 0L) {
        presenter.update {
            it.copy(
                selectedQualityLabel = qualityLabel,
                playback = null,
                pendingSeekMs = resumePositionMs.coerceAtLeast(0L),
                failedStreamUrls = emptySet(),
                recoveryAttempted = false,
            )
        }
        persistSelection()
        load(forceRefresh = true)
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
        val state = presenter.state.value
        val playback = state.playback ?: return
        val episode = state.episodes.firstOrNull { it.id == state.currentEpisodeId } ?: return
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
        )
    }

    private fun persistSelection() {
        val state = presenter.state.value
        val previousSelection = watchStateRepository.getSelectedSource(titleId)
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
        presenter.update { it.copy(pendingSeekMs = 0L) }
    }

    fun playPreviousEpisode() {
        resolveAdjacentEpisode(
            episodes = presenter.state.value.episodes,
            currentEpisodeId = presenter.state.value.currentEpisodeId,
            currentEpisodeNumber = presenter.state.value.currentEpisodeNumber,
            offset = -1,
        )?.let { selectEpisode(it.id, episodeNumberHint = it.number) }
    }

    fun playNextEpisode() {
        resolveAdjacentEpisode(
            episodes = presenter.state.value.episodes,
            currentEpisodeId = presenter.state.value.currentEpisodeId,
            currentEpisodeNumber = presenter.state.value.currentEpisodeNumber,
            offset = 1,
        )?.let { selectEpisode(it.id, episodeNumberHint = it.number) }
    }

    override fun onCleared() {
        loadJob?.cancel()
        settingsLoadJob?.cancel()
        repository.close()
        super.onCleared()
    }

    private fun restoreSavedSeek() {
        val state = presenter.state.value
        val savedSeekMs = findSavedSeekMs(
            episodeId = state.currentEpisodeId,
            episodes = state.episodes,
        ) ?: return
        presenter.update { it.copy(pendingSeekMs = savedSeekMs) }
    }

    private fun findSavedSeekMs(
        episodeId: String,
        episodes: List<WatchEpisode>,
    ): Long? {
        val exactProgress = watchStateRepository.getEpisodeProgress(titleId, episodeId)
        val episodeNumber = episodes.firstOrNull { it.id == episodeId }?.number
        val numberProgress = episodeNumber?.let { number ->
            watchStateRepository.getEpisodeProgress(titleId)
                .filter { it.episodeNumber == number }
                .maxByOrNull { it.updatedAt }
        }
        val progress = exactProgress ?: numberProgress ?: return null
        return resumablePlaybackPositionMs(progress.positionMs, progress.durationMs)
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
    return resolveResumablePlaybackPosition(positionMs, durationMs)
}

private fun <T> Result<T>.throwIfCancelled(): Result<T> {
    val error = exceptionOrNull()
    if (error is CancellationException) throw error
    return this
}

private const val PLAYBACK_LOG_TAG = "HibikiPlayback"
