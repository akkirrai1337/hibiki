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
import org.akkirrai.hibiki.core.source.AnimeWatchRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository

class PlayerViewModel(
    sourceId: String,
    episodeId: String,
    initialEpisodeNumber: Double?,
    private val repository: AnimeWatchRepository,
    private val watchStateRepository: WatchStateRepository,
    private val offlineDownloadRepository: OfflineDownloadRepository,
    private val offlineTitleMetadataRepository: OfflineTitleMetadataRepository,
) : ViewModel() {
    private val titleId = sourceId.substringBefore(':')
    private var loadJob: Job? = null
    private var settingsLoadJob: Job? = null
    private val savedSelection = watchStateRepository.getSelectedSource(titleId)
        .takeIf { it.sourceId == sourceId }
    private val _uiState = MutableStateFlow(
        PlayerUiState(
            currentSourceId = sourceId,
            currentEpisodeId = episodeId,
            currentEpisodeNumber = initialEpisodeNumber,
            selectedProviderId = savedSelection?.backendId,
            selectedPlayerName = savedSelection?.playerName,
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
                    _uiState.update {
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
                    _uiState.update {
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
        if (episodeId == _uiState.value.currentEpisodeId && _uiState.value.playback != null) {
            return
        }
        settingsLoadJob?.cancel()
        _uiState.update { currentState ->
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
        val state = _uiState.value
        if (state.isLoading || state.recoveryAttempted) {
            return
        }
        val excluded = streamUrl?.takeIf(String::isNotBlank)?.let { setOf(it) } ?: emptySet()
        load(forceRefresh = true, excludedStreamUrls = excluded)
    }

    fun loadSettingsOptions() {
        val state = _uiState.value
        val optionsKey = state.settingsOptionsKey()
        if (state.settingsOptionsKey == optionsKey || state.isSettingsLoading) {
            return
        }
        settingsLoadJob?.cancel()
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
                return@launch
            }
            result
                .onSuccess { options ->
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
        viewModelScope.launch(Dispatchers.IO) {
            val episodes = repository.getEpisodes(source.sourceId)
            val matching = episodes.firstOrNull { it.number == currentEpisode.number } ?: episodes.firstOrNull() ?: return@launch
            _uiState.update {
                it.copy(
                    currentSourceId = source.sourceId,
                    currentEpisodeId = matching.id,
                    episodes = episodes,
                    selectedProviderId = null,
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
                backendId = null,
                autoSelect = false,
            )
            restoreSavedSeek()
            load(forceRefresh = true)
        }
    }

    fun selectBackend(providerId: String?, resumePositionMs: Long = 0L) {
        settingsLoadJob?.cancel()
        _uiState.update {
            it.copy(
                selectedProviderId = providerId,
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

    fun selectPlayer(playerName: String?, resumePositionMs: Long = 0L) {
        settingsLoadJob?.cancel()
        _uiState.update {
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
        _uiState.update {
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
        if (durationMs <= 0L || safePositionMs <= 0L) {
            return
        }
        val state = _uiState.value
        val playback = state.playback ?: return
        val episode = state.episodes.firstOrNull { it.id == state.currentEpisodeId } ?: return
        watchStateRepository.saveSelectedSource(
            titleId = titleId,
            sourceId = state.currentSourceId,
            sourceTitle = playback.sourceTitle,
            quality = playback.qualityLabel,
            playerName = state.selectedPlayerName,
            backendId = state.selectedProviderId,
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
            durationMs = durationMs,
        )
    }

    private fun persistSelection() {
        val state = _uiState.value
        val previousSelection = watchStateRepository.getSelectedSource(titleId)
        watchStateRepository.saveSelectedSource(
            titleId = titleId,
            sourceId = state.currentSourceId,
            sourceTitle = state.playback?.sourceTitle ?: previousSelection.sourceTitle,
            quality = state.selectedQualityLabel ?: state.playback?.qualityLabel,
            playerName = state.selectedPlayerName,
            backendId = state.selectedProviderId,
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
        val exactProgress = watchStateRepository.getEpisodeProgress(titleId, episodeId)
        val episodeNumber = episodes.firstOrNull { it.id == episodeId }?.number
        val numberProgress = episodeNumber?.let { number ->
            watchStateRepository.getEpisodeProgress(titleId)
                .filter { it.episodeNumber == number }
                .maxByOrNull { it.updatedAt }
        }
        return (exactProgress ?: numberProgress)
            ?.positionMs
            ?.coerceAtLeast(0L)
            ?.takeIf { it > 0L }
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
            ?: watchStateRepository.getEpisodeProgress(titleId, requestedEpisodeId)?.episodeNumber

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

data class PlayerUiState(
    val isLoading: Boolean = true,
    val playback: PlaybackStream? = null,
    val animeTitle: String = "",
    val episodes: List<WatchEpisode> = emptyList(),
    val currentSourceId: String = "",
    val currentEpisodeId: String = "",
    val currentEpisodeNumber: Double? = null,
    val pendingSeekMs: Long = 0L,
    val errorMessage: String? = null,
    val failedStreamUrls: Set<String> = emptySet(),
    val recoveryAttempted: Boolean = false,
    val isSettingsLoading: Boolean = false,
    val settingsOptions: PlaybackSettingsOptions = PlaybackSettingsOptions(),
    val settingsOptionsKey: String? = null,
    val selectedProviderId: String? = null,
    val selectedPlayerName: String? = null,
    val selectedQualityLabel: String? = null,
)

private fun PlayerUiState.settingsOptionsKey(): String =
    buildString {
        append(currentSourceId)
        append(':')
        append(currentEpisodeId)
        append(':')
        append(selectedProviderId.orEmpty())
        append(':')
        append(selectedPlayerName.orEmpty())
        append(':')
        append(selectedQualityLabel.orEmpty())
    }

private fun <T> Result<T>.throwIfCancelled(): Result<T> {
    val error = exceptionOrNull()
    if (error is CancellationException) throw error
    return this
}

private const val PLAYBACK_LOG_TAG = "HibikiPlayback"
