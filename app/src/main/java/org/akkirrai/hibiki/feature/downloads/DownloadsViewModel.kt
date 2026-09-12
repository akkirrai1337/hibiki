package org.akkirrai.hibiki.feature.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.download.OfflineEpisodeDownloadState
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository

class DownloadsViewModel(
    context: Context,
    offlineDownloadRepository: OfflineDownloadRepository? = null,
    offlineTitleMetadataRepository: OfflineTitleMetadataRepository? = null,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val offlineDownloadRepository = offlineDownloadRepository
        ?: OfflineDownloadRepository(appContext)
    private val offlineTitleMetadataRepository = offlineTitleMetadataRepository
        ?: OfflineTitleMetadataRepository(appContext)
    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val entries = offlineDownloadRepository.getOfflineTitleIds().mapNotNull { titleId ->
                val anime = offlineTitleMetadataRepository.get(titleId) ?: return@mapNotNull null
                val sources = offlineDownloadRepository.getOfflineSources(titleId)
                    .mapNotNull { source -> buildSourceEntry(source) }
                if (sources.isEmpty()) return@mapNotNull null
                DownloadedTitleEntry(anime = anime, sources = sources)
            }
            val sorted = entries.sortedWith(
                compareByDescending<DownloadedTitleEntry> { it.hasActiveDownloads }
                    .thenBy { it.anime.title.lowercase() },
            )
            _uiState.update { it.copy(entries = sorted, isLoading = false) }
        }
    }

    private fun buildSourceEntry(source: WatchSource): DownloadedSourceEntry? {
        val episodes = offlineDownloadRepository.getOfflineEpisodes(source.sourceId)
        if (episodes.isEmpty()) return null
        val states = offlineDownloadRepository.getEpisodeStates(
            sourceId = source.sourceId,
            episodeIds = episodes.map { it.id },
        )
        val completedCount = states.values.count { it == OfflineEpisodeDownloadState.Completed }
        val activeCount = states.values.count { it.isActiveDownload() }
        return DownloadedSourceEntry(
            source = source,
            completedCount = completedCount,
            activeCount = activeCount,
            totalCount = episodes.size,
        )
    }

    private fun OfflineEpisodeDownloadState.isActiveDownload(): Boolean = when (this) {
        OfflineEpisodeDownloadState.Queued,
        is OfflineEpisodeDownloadState.Downloading,
        OfflineEpisodeDownloadState.Paused -> true
        OfflineEpisodeDownloadState.NotDownloaded,
        OfflineEpisodeDownloadState.Completed,
        OfflineEpisodeDownloadState.Failed -> false
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dependencies = context.applicationContext.hibikiDependencies()
            return DownloadsViewModel(
                context = context.applicationContext,
                offlineDownloadRepository = dependencies.offlineDownloadRepository(),
                offlineTitleMetadataRepository = dependencies.offlineTitleMetadataRepository(),
            ) as T
        }
    }
}

data class DownloadsUiState(
    val entries: List<DownloadedTitleEntry> = emptyList(),
    val isLoading: Boolean = true,
)

data class DownloadedTitleEntry(
    val anime: Anime,
    val sources: List<DownloadedSourceEntry>,
) {
    val hasActiveDownloads: Boolean
        get() = sources.any { it.activeCount > 0 }
}

data class DownloadedSourceEntry(
    val source: WatchSource,
    val completedCount: Int,
    val activeCount: Int,
    val totalCount: Int,
)
