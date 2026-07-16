package org.akkirrai.hibiki.core.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.core.model.PlaybackStream
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.model.WatchSource

class OfflineDownloadRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    init {
        OfflineDownloadQueue.installInBackground(appContext)
    }

    suspend fun enqueueEpisodes(
        source: WatchSource,
        episodes: List<WatchEpisode>,
    ): Int = withContext(Dispatchers.IO) {
        OfflineDownloadQueue.enqueue(
            context = appContext,
            source = source,
            episodes = episodes,
        )
    }

    fun getOfflinePlayback(
        sourceId: String,
        episodeId: String,
    ): PlaybackStream? {
        return OfflineDownloadQueue.getOfflinePlayback(
            context = appContext,
            sourceId = sourceId,
            episodeId = episodeId,
        )
    }

    fun getEpisodeStates(
        sourceId: String,
        episodeIds: List<String>,
    ): Map<String, OfflineEpisodeDownloadState> {
        return OfflineDownloadQueue.getEpisodeStates(
            context = appContext,
            sourceId = sourceId,
            episodeIds = episodeIds,
        )
    }

    fun getOfflineTitleIds(): List<String> {
        return OfflineDownloadQueue.getOfflineTitleIds(context = appContext)
    }

    fun getOfflineSources(titleId: String): List<WatchSource> {
        return OfflineDownloadQueue.getOfflineSources(
            context = appContext,
            titleId = titleId,
        )
    }

    fun getOfflineEpisodes(sourceId: String): List<WatchEpisode> {
        return OfflineDownloadQueue.getOfflineEpisodes(
            context = appContext,
            sourceId = sourceId,
        )
    }

    fun pauseEpisode(sourceId: String, episodeId: String) {
        OfflineDownloadQueue.pauseEpisode(appContext, sourceId, episodeId)
    }

    fun resumeEpisode(sourceId: String, episodeId: String) {
        OfflineDownloadQueue.resumeEpisode(appContext, sourceId, episodeId)
    }

    fun removeEpisode(sourceId: String, episodeId: String) {
        OfflineDownloadQueue.removeEpisode(appContext, sourceId, episodeId)
    }
}
