package org.akkirrai.hibiki.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.core.model.PlaybackStream
import org.akkirrai.hibiki.core.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.source.AnimeWatchRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository

class PlayerViewModel(
    sourceId: String,
    episodeId: String,
    context: Context,
    private val repository: AnimeWatchRepository = AnimeWatchRepository(context.applicationContext),
) : ViewModel() {
    private val titleId = sourceId.substringBefore(':')
    private val watchStateRepository = WatchStateRepository(context.applicationContext)
    private val offlineDownloadRepository = OfflineDownloadRepository(context = context.applicationContext)
    private var loadJob: Job? = null
    private var settingsLoadJob: Job? = null
    private val _uiState = MutableStateFlow(
        PlayerUiState(
            currentSourceId = sourceId,
            currentEpisodeId = episodeId,
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
            val episodesDeferred = async {
                runCatching {
                    offlineDownloadRepository.getOfflineEpisodes(state.currentSourceId)
                        .takeIf { it.isNotEmpty() }
                        ?: repository.getEpisodes(state.currentSourceId)
                }
            }
            val playbackResult = runCatching {
                val offlinePlayback = offlineDownloadRepository.getOfflinePlayback(
                    sourceId = state.currentSourceId,
                    episodeId = state.currentEpisodeId,
                )
                offlinePlayback
                    ?.takeIf { it.streamUrl !in excludedStreamUrls }
                    ?: repository.resolveStream(
                        sourceId = state.currentSourceId,
                        episodeId = state.currentEpisodeId,
                        forceRefresh = forceRefresh,
                        excludedStreamUrls = excludedStreamUrls,
                        preferredProviderId = state.selectedProviderId,
                        preferredPlayerName = state.selectedPlayerName,
                        preferredQuality = state.selectedQualityLabel,
                    )
            }
            val episodesResult = episodesDeferred.await()
            val currentState = _uiState.value
            if (currentState.currentSourceId != state.currentSourceId || currentState.currentEpisodeId != state.currentEpisodeId) {
                return@launch
            }
            val episodes = episodesResult.getOrDefault(currentState.episodes)

            playbackResult
                .onSuccess { stream ->
                    val savedSeekMs = findSavedSeekMs(
                        episodeId = state.currentEpisodeId,
                        episodes = episodes,
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            playback = stream,
                            errorMessage = null,
                            episodes = episodes,
                            pendingSeekMs = savedSeekMs ?: it.pendingSeekMs,
                            failedStreamUrls = it.failedStreamUrls - stream.streamUrl,
                            recoveryAttempted = false,
                            selectedQualityLabel = stream.qualityLabel ?: it.selectedQualityLabel,
                        )
                    }
                    loadSettingsOptions()
                }
                .onFailure { throwable ->
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
                            recoveryAttempted = false,
                        )
                    }
                }
        }
    }

    fun selectEpisode(episodeId: String, resumePositionMs: Long = 0L) {
        if (episodeId == _uiState.value.currentEpisodeId && _uiState.value.playback != null) {
            return
        }
        settingsLoadJob?.cancel()
            _uiState.update {
                it.copy(
                    currentEpisodeId = episodeId,
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
                    preferredProviderId = state.selectedProviderId,
                )
            }
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

    fun selectVoiceover(source: WatchSource) {
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
                    settingsOptionsKey = null,
                    failedStreamUrls = emptySet(),
                    recoveryAttempted = false,
                )
            }
            restoreSavedSeek()
            load(forceRefresh = true)
        }
    }

    fun selectBackend(providerId: String?) {
        settingsLoadJob?.cancel()
        _uiState.update {
            it.copy(
                selectedProviderId = providerId,
                settingsOptionsKey = null,
                failedStreamUrls = emptySet(),
                recoveryAttempted = false,
            )
        }
        load(forceRefresh = true)
    }

    fun selectPlayer(playerName: String?) {
        settingsLoadJob?.cancel()
        _uiState.update {
            it.copy(
                selectedPlayerName = playerName,
                settingsOptionsKey = null,
                failedStreamUrls = emptySet(),
                recoveryAttempted = false,
            )
        }
        load(forceRefresh = true)
    }

    fun selectQuality(qualityLabel: String?) {
        _uiState.update {
            it.copy(
                selectedQualityLabel = qualityLabel,
                failedStreamUrls = emptySet(),
                recoveryAttempted = false,
            )
        }
        load(forceRefresh = true)
    }

    fun savePlaybackProgress(
        positionMs: Long,
        durationMs: Long,
    ) {
        if (durationMs <= 0L) {
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
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }

    fun consumePendingSeek() {
        _uiState.update { it.copy(pendingSeekMs = 0L) }
    }

    fun playPreviousEpisode() {
        adjacentEpisode(offset = -1)?.let { selectEpisode(it.id) }
    }

    fun playNextEpisode() {
        adjacentEpisode(offset = 1)?.let { selectEpisode(it.id) }
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
        if (currentIndex == -1) {
            return null
        }
        return state.episodes.getOrNull(currentIndex + offset)
    }

    class Factory(
        private val sourceId: String,
        private val episodeId: String,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(
                sourceId = sourceId,
                episodeId = episodeId,
                context = context,
            ) as T
        }
    }
}

data class PlayerUiState(
    val isLoading: Boolean = true,
    val playback: PlaybackStream? = null,
    val episodes: List<WatchEpisode> = emptyList(),
    val currentSourceId: String = "",
    val currentEpisodeId: String = "",
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

private const val PLAYBACK_LOG_TAG = "HibikiPlayback"
