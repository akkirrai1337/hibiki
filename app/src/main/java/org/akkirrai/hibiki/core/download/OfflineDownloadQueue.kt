@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.akkirrai.hibiki.core.download

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.core.model.PlaybackSegment
import org.akkirrai.hibiki.core.model.PlaybackSegmentType
import org.akkirrai.hibiki.core.model.PlaybackStream
import org.akkirrai.hibiki.core.model.PlaybackStreamType
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.source.AnimeWatchRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object OfflineDownloadQueue {
    private const val TAG = "OfflineDownloadQueue"
    private const val PREFS_NAME = "hibiki_offline_downloads"
    private const val QUEUE_KEY = "pending_queue"
    private const val STORED_EPISODES_KEY = "stored_episodes"
    private const val FAILED_EPISODES_KEY = "failed_episodes"
    private const val SESSION_TOTAL_KEY = "session_total"
    private const val SESSION_COMPLETED_KEY = "session_completed"
    private const val SESSION_COMPLETED_IDS_KEY = "session_completed_ids"
    private const val SESSION_DOWNLOAD_IDS_KEY = "session_download_ids"
    private const val MAX_ACTIVE_DOWNLOADS = 2
    private const val STOP_REASON_PAUSED_BY_USER = 1
    private const val STREAM_RESOLVE_RETRY_DELAY_MS = 500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processingLock = Any()
    private val requestLock = Any()
    private val installedManagers = mutableSetOf<Int>()
    private val downloadsBeingRemoved = mutableSetOf<String>()

    @Volatile
    private var isProcessing = false

    fun installInBackground(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            install(appContext)
        }
    }

    fun install(
        context: Context,
        manager: DownloadManager = OfflineMediaCache.getDownloadManager(context),
    ) {
        manager.setMaxParallelDownloads(MAX_ACTIVE_DOWNLOADS)
        val managerKey = System.identityHashCode(manager)
        synchronized(installedManagers) {
            if (!installedManagers.add(managerKey)) {
                return@synchronized
            }
            manager.addListener(
                object : DownloadManager.Listener {
                    override fun onInitialized(downloadManager: DownloadManager) {
                        drain(context.applicationContext, downloadManager)
                    }

                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        if (finalException != null) {
                            AppLogger.e(
                                TAG,
                                "Media3 download failed: id=${download.request.id}",
                                finalException,
                            )
                        }
                        if (download.state == Download.STATE_COMPLETED) {
                            incrementSessionCompleted(context.applicationContext, download.request.id)
                        }
                        if (download.state == Download.STATE_COMPLETED || download.state == Download.STATE_FAILED) {
                            drain(context.applicationContext, downloadManager)
                        }
                        if (shouldResetSessionProgress(context.applicationContext, downloadManager)) {
                            resetSessionProgress(context.applicationContext)
                        }
                    }

                    override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                        synchronized(requestLock) {
                            downloadsBeingRemoved.remove(download.request.id)
                        }
                        drain(context.applicationContext, downloadManager)
                    }

                    override fun onIdle(downloadManager: DownloadManager) {
                        drain(context.applicationContext, downloadManager)
                        if (shouldResetSessionProgress(context.applicationContext, downloadManager)) {
                            resetSessionProgress(context.applicationContext)
                        }
                    }
                }
            )
        }
        drain(context.applicationContext, manager)
    }

    fun enqueue(
        context: Context,
        source: WatchSource,
        episodes: List<WatchEpisode>,
    ): Int {
        val appContext = context.applicationContext
        if (episodes.isEmpty()) return 0
        val entries = synchronized(requestLock) {
            val existingIds = pendingEntries(appContext)
                .map { it.downloadId }
                .toMutableSet()
            val newEntries = episodes.map { episode ->
                PendingEpisode(
                    sourceId = source.sourceId,
                    sourceTitle = source.title,
                    sourceEpisodeCount = source.episodeCount,
                    sourceQualityLabel = source.qualityLabel,
                    sourceIsPriority = source.isPriority,
                    episodeId = episode.id,
                    episodeNumber = episode.number,
                    episodeTitle = episode.title,
                )
            }.filter { existingIds.add(it.downloadId) }
            if (newEntries.isNotEmpty()) {
                clearFailedEntries(appContext, newEntries.map { it.downloadId }.toSet())
                savePendingEntries(appContext, pendingEntries(appContext) + newEntries)
                saveStoredEntries(appContext, mergeStoredEntries(storedEntries(appContext), newEntries))
                addToSession(appContext, newEntries.mapTo(mutableSetOf()) { it.downloadId })
            }
            newEntries
        }
        if (entries.isEmpty()) return 0
        val manager = OfflineMediaCache.getDownloadManager(appContext)
        install(appContext, manager)
        drain(appContext, manager)
        return entries.size
    }


    fun getEpisodeStates(
        context: Context,
        sourceId: String,
        episodeIds: List<String>,
    ): Map<String, OfflineEpisodeDownloadState> {
        if (episodeIds.isEmpty()) return emptyMap()
        val manager = OfflineMediaCache.getDownloadManager(context)
        install(context, manager)
        val pendingIds = pendingEntries(context)
            .filter { it.sourceId == sourceId }
            .map { it.episodeId }
            .toSet()
        val failedIds = failedEntryIds(context)
        return buildMap {
            episodeIds.forEach { episodeId ->
                val id = downloadId(sourceId, episodeId)
                val current = manager.currentDownloads.firstOrNull { it.request.id == id }
                val stored = current ?: runCatching { manager.downloadIndex.getDownload(id) }.getOrNull()
                val state = when {
                    episodeId in pendingIds -> OfflineEpisodeDownloadState.Queued
                    stored != null -> stored.toEpisodeDownloadState()
                    id in failedIds -> OfflineEpisodeDownloadState.Failed
                    else -> OfflineEpisodeDownloadState.NotDownloaded
                }
                put(episodeId, state)
            }
        }
    }

    fun pauseEpisode(
        context: Context,
        sourceId: String,
        episodeId: String,
    ) {
        DownloadService.sendSetStopReason(
            context.applicationContext,
            HibikiDownloadService::class.java,
            downloadId(sourceId, episodeId),
            STOP_REASON_PAUSED_BY_USER,
            false,
        )
    }

    fun resumeEpisode(
        context: Context,
        sourceId: String,
        episodeId: String,
    ) {
        DownloadService.sendSetStopReason(
            context.applicationContext,
            HibikiDownloadService::class.java,
            downloadId(sourceId, episodeId),
            Download.STOP_REASON_NONE,
            false,
        )
    }

    fun removeEpisode(
        context: Context,
        sourceId: String,
        episodeId: String,
    ) {
        val appContext = context.applicationContext
        val id = downloadId(sourceId, episodeId)
        val manager = OfflineMediaCache.getDownloadManager(appContext)
        install(appContext, manager)
        synchronized(requestLock) {
            savePendingEntries(
                appContext,
                pendingEntries(appContext).filterNot { it.downloadId == id },
            )
            saveStoredEntries(
                appContext,
                storedEntries(appContext).filterNot { it.downloadId == id },
            )
            clearFailedEntries(appContext, setOf(id))
            removeFromSession(appContext, id)
            prefs(appContext).edit()
                .remove(playbackKey(sourceId, episodeId))
                .apply()
            val isManagedDownload = manager.currentDownloads.any { it.request.id == id } ||
                runCatching { manager.downloadIndex.getDownload(id) != null }.getOrDefault(false)
            if (isManagedDownload) {
                downloadsBeingRemoved.add(id)
            }
            val removedDirectly = runCatching {
                manager.removeDownload(id)
            }.isSuccess
            if (!removedDirectly) {
                DownloadService.sendRemoveDownload(
                    appContext,
                    HibikiDownloadService::class.java,
                    id,
                    false,
                )
            }
        }
        drain(appContext, manager)
    }

    fun getOfflinePlayback(
        context: Context,
        sourceId: String,
        episodeId: String,
    ): PlaybackStream? {
        val state = getEpisodeStates(
            context = context,
            sourceId = sourceId,
            episodeIds = listOf(episodeId),
        )[episodeId]
        if (state != OfflineEpisodeDownloadState.Completed) {
            return null
        }
        val encoded = prefs(context).getString(playbackKey(sourceId, episodeId), null) ?: return null
        return runCatching { decodePlayback(JSONObject(encoded)) }.getOrNull()
    }

    fun getOfflineTitleIds(context: Context): List<String> {
        return visibleStoredEntries(context)
            .map { entry -> entry.sourceId.substringBefore(':') }
            .filter(String::isNotBlank)
            .distinct()
    }

    fun getOfflineSources(
        context: Context,
        titleId: String,
    ): List<WatchSource> {
        val entries = visibleStoredEntries(context)
            .filter { it.sourceId.substringBefore(':') == titleId }
        return entries
            .groupBy { it.sourceId }
            .values
            .map { grouped -> grouped.first().toWatchSource() }
            .sortedWith(compareByDescending<WatchSource> { it.isPriority }.thenBy { it.title.lowercase() })
    }

    fun getOfflineEpisodes(
        context: Context,
        sourceId: String,
    ): List<WatchEpisode> {
        return visibleStoredEntries(context)
            .filter { it.sourceId == sourceId }
            .distinctBy { it.episodeId }
            .sortedBy { it.episodeNumber }
            .map { it.toWatchEpisode() }
    }

    private fun drain(
        context: Context,
        manager: DownloadManager,
    ) {
        val freeSlots = MAX_ACTIVE_DOWNLOADS - activeDownloadCount(manager)
        if (freeSlots <= 0) return
        synchronized(processingLock) {
            if (isProcessing) return
            isProcessing = true
        }

        val removingIds = synchronized(requestLock) {
            manager.currentDownloads
                .filter { it.state == Download.STATE_REMOVING }
                .mapTo(downloadsBeingRemoved.toMutableSet()) { it.request.id }
        }
        val entries = synchronized(requestLock) {
            takePendingEntries(context, freeSlots, removingIds)
        }
        if (entries.isEmpty()) {
            synchronized(processingLock) { isProcessing = false }
            return
        }

        scope.launch {
            val repository = AnimeWatchRepository(context.applicationContext)
            var addedAny = false
            try {
                entries.forEach { entry ->
                    runCatching {
                        val source = entry.toWatchSource()
                        val episode = entry.toWatchEpisode()
                        val playback = resolveStreamForDownload(
                            repository = repository,
                            source = source,
                            episode = episode,
                        )
                        synchronized(requestLock) {
                            if (!isCurrentRequest(context, entry)) return@runCatching
                            saveOfflinePlayback(
                                context = context,
                                sourceId = source.sourceId,
                                episodeId = episode.id,
                                playback = playback,
                            )
                            clearFailedEntries(context, setOf(entry.downloadId))
                            DownloadService.sendAddDownload(
                                context,
                                HibikiDownloadService::class.java,
                                playback.toDownloadRequest(source, episode),
                                false,
                            )
                            addedAny = true
                        }
                    }.onFailure { error ->
                        AppLogger.e(
                            TAG,
                            "Failed to prepare offline episode: id=${entry.downloadId}",
                            error,
                        )
                        markFailedEntry(context, entry)
                    }
                }
            } finally {
                repository.close()
                synchronized(processingLock) { isProcessing = false }
                if (!addedAny) {
                    drain(context, manager)
                }
            }
        }
    }


    private fun Download.toEpisodeDownloadState(): OfflineEpisodeDownloadState {
        return when (state) {
            Download.STATE_COMPLETED -> OfflineEpisodeDownloadState.Completed
            Download.STATE_DOWNLOADING -> OfflineEpisodeDownloadState.Downloading(
                progress = percentDownloaded.takeIf { it >= 0f }?.div(100f)?.coerceIn(0f, 1f) ?: 0f,
            )
            Download.STATE_QUEUED,
            Download.STATE_RESTARTING -> OfflineEpisodeDownloadState.Queued
            Download.STATE_STOPPED -> OfflineEpisodeDownloadState.Paused
            Download.STATE_FAILED -> OfflineEpisodeDownloadState.Failed
            Download.STATE_REMOVING -> OfflineEpisodeDownloadState.NotDownloaded
            else -> OfflineEpisodeDownloadState.NotDownloaded
        }
    }

    private suspend fun resolveStreamForDownload(
        repository: AnimeWatchRepository,
        source: WatchSource,
        episode: WatchEpisode,
    ): PlaybackStream {
        return try {
            repository.resolveFastestStream(
                sourceId = source.sourceId,
                episodeId = episode.id,
                forceRefresh = false,
            )
        } catch (error: Throwable) {
            if (!error.isStreamResolveTimeout()) throw error
            AppLogger.w(
                TAG,
                "Stream resolve timed out; retrying once: id=${downloadId(source.sourceId, episode.id)}",
                error,
            )
            delay(STREAM_RESOLVE_RETRY_DELAY_MS)
            repository.resolveFastestStream(
                sourceId = source.sourceId,
                episodeId = episode.id,
                forceRefresh = true,
            )
        }
    }

    private fun Throwable.isStreamResolveTimeout(): Boolean {
        val normalizedMessage = message.orEmpty().lowercase()
        return "timeout" in normalizedMessage ||
            "timed out" in normalizedMessage ||
            "тайм" in normalizedMessage
    }

    private fun activeDownloadCount(manager: DownloadManager): Int {
        return manager.currentDownloads.count { download ->
            download.state == Download.STATE_QUEUED ||
                download.state == Download.STATE_DOWNLOADING ||
                download.state == Download.STATE_RESTARTING
        }
    }

    private fun pendingEntries(context: Context): List<PendingEpisode> {
        val raw = prefs(context).getString(QUEUE_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    PendingEpisode.fromJson(item)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun savePendingEntries(context: Context, entries: List<PendingEpisode>) {
        prefs(context).edit()
            .putString(
                QUEUE_KEY,
                JSONArray().apply { entries.forEach { put(it.toJson()) } }.toString(),
            )
            .apply()
    }

    private fun storedEntries(context: Context): List<PendingEpisode> {
        val raw = prefs(context).getString(STORED_EPISODES_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    PendingEpisode.fromJson(item)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveStoredEntries(context: Context, entries: List<PendingEpisode>) {
        prefs(context).edit()
            .putString(
                STORED_EPISODES_KEY,
                JSONArray().apply { entries.forEach { put(it.toJson()) } }.toString(),
            )
            .apply()
    }

    private fun failedEntryIds(context: Context): Set<String> {
        val raw = prefs(context).getStringSet(FAILED_EPISODES_KEY, emptySet()) ?: emptySet()
        return raw.filter(String::isNotBlank).toSet()
    }

    private fun markFailedEntry(context: Context, entry: PendingEpisode) {
        saveStoredEntries(context, mergeStoredEntries(storedEntries(context), listOf(entry)))
        prefs(context).edit()
            .putStringSet(FAILED_EPISODES_KEY, failedEntryIds(context) + entry.downloadId)
            .apply()
    }

    private fun clearFailedEntries(context: Context, ids: Set<String>) {
        if (ids.isEmpty()) return
        val updated = failedEntryIds(context) - ids
        prefs(context).edit()
            .putStringSet(FAILED_EPISODES_KEY, updated)
            .apply()
    }

    private fun mergeStoredEntries(
        existing: List<PendingEpisode>,
        added: List<PendingEpisode>,
    ): List<PendingEpisode> {
        return (existing + added)
            .associateBy(PendingEpisode::downloadId)
            .values
            .toList()
    }

    private fun takePendingEntries(
        context: Context,
        count: Int,
        blockedIds: Set<String>,
    ): List<PendingEpisode> {
        val entries = pendingEntries(context)
        val selected = entries.filterNot { it.downloadId in blockedIds }.take(count)
        if (selected.isNotEmpty()) {
            val selectedTokens = selected.mapTo(mutableSetOf()) { it.requestToken }
            savePendingEntries(context, entries.filterNot { it.requestToken in selectedTokens })
        }
        return selected
    }

    private fun isCurrentRequest(context: Context, entry: PendingEpisode): Boolean {
        return storedEntries(context).any {
            it.downloadId == entry.downloadId && it.requestToken == entry.requestToken
        }
    }

    private fun visibleStoredEntries(context: Context): List<PendingEpisode> {
        val stored = storedEntries(context)
        if (stored.isEmpty()) return emptyList()
        return stored.filter { entry ->
            when (episodeState(context, entry)) {
                OfflineEpisodeDownloadState.NotDownloaded,
                OfflineEpisodeDownloadState.Failed -> false
                else -> true
            }
        }
    }

    private fun episodeState(
        context: Context,
        entry: PendingEpisode,
    ): OfflineEpisodeDownloadState {
        return getEpisodeStates(
            context = context,
            sourceId = entry.sourceId,
            episodeIds = listOf(entry.episodeId),
        )[entry.episodeId] ?: OfflineEpisodeDownloadState.NotDownloaded
    }

    private fun saveOfflinePlayback(
        context: Context,
        sourceId: String,
        episodeId: String,
        playback: PlaybackStream,
    ) {
        prefs(context).edit()
            .putString(playbackKey(sourceId, episodeId), encodePlayback(playback).toString())
            .apply()
    }

    private fun PlaybackStream.toDownloadRequest(
        source: WatchSource,
        episode: WatchEpisode,
    ): DownloadRequest {
        return DownloadRequest.Builder(downloadId(source.sourceId, episode.id), streamUrl.toUri())
            .setMimeType(streamType.toMimeType())
            .setData(
                JSONObject()
                    .put("animeTitle", this.animeTitle)
                    .put("sourceTitle", source.title)
                    .put("episodeId", episode.id)
                    .put("episodeNumber", episode.number)
                    .put("episodeTitle", episode.title)
                    .toString()
                    .toByteArray(Charsets.UTF_8),
            )
            .build()
    }

    private fun encodePlayback(playback: PlaybackStream): JSONObject {
        return JSONObject().apply {
            put("animeTitle", playback.animeTitle)
            put("sourceTitle", playback.sourceTitle)
            put("episodeTitle", playback.episodeTitle)
            put("streamUrl", playback.streamUrl)
            put("streamType", playback.streamType.name)
            put("qualityLabel", playback.qualityLabel)
            put("headers", JSONObject().apply {
                playback.headers.forEach { (key, value) -> put(key, value) }
            })
            put("segments", JSONArray().apply {
                playback.segments.forEach { segment ->
                    put(JSONObject().apply {
                        put("type", segment.type.name)
                        put("startMs", segment.startMs)
                        put("endMs", segment.endMs)
                    })
                }
            })
        }
    }

    private fun decodePlayback(json: JSONObject): PlaybackStream {
        return PlaybackStream(
            animeTitle = json.optString("animeTitle").ifBlank { "" },
            sourceTitle = json.optString("sourceTitle").ifBlank { "Озвучка" },
            episodeTitle = json.optString("episodeTitle").ifBlank { "" },
            streamUrl = json.getString("streamUrl"),
            streamType = runCatching { PlaybackStreamType.valueOf(json.getString("streamType")) }
                .getOrDefault(PlaybackStreamType.HLS),
            qualityLabel = json.optString("qualityLabel").ifBlank { null },
            headers = json.optJSONObject("headers").toStringMap(),
            segments = json.optJSONArray("segments").toPlaybackSegments(),
        )
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return buildMap {
            keys().forEach { key ->
                val value = optString(key)
                if (key.isNotBlank() && value.isNotBlank()) {
                    put(key, value)
                }
            }
        }
    }

    private fun JSONArray?.toPlaybackSegments(): List<PlaybackSegment> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val type = runCatching { PlaybackSegmentType.valueOf(item.optString("type")) }
                    .getOrDefault(PlaybackSegmentType.Unknown)
                val startMs = item.optLong("startMs")
                val endMs = item.optLong("endMs")
                if (endMs > startMs) {
                    add(PlaybackSegment(type = type, startMs = startMs, endMs = endMs))
                }
            }
        }
    }

    private fun PlaybackStreamType.toMimeType(): String {
        return when (this) {
            PlaybackStreamType.HLS -> MimeTypes.APPLICATION_M3U8
            PlaybackStreamType.MP4 -> MimeTypes.VIDEO_MP4
            PlaybackStreamType.DASH -> MimeTypes.APPLICATION_MPD
        }
    }

    private fun playbackKey(sourceId: String, episodeId: String): String =
        "offline_playback:${downloadId(sourceId, episodeId)}"

    fun getPendingCount(context: Context): Int = pendingEntries(context).size

    fun getTotalQueuedCount(context: Context): Int = storedEntries(context).size

    fun getNotificationProgress(context: Context): Pair<Int, Int> {
        val total = prefs(context).getInt(SESSION_TOTAL_KEY, 0)
        val completed = prefs(context).getInt(SESSION_COMPLETED_KEY, 0)
        return Pair(completed, total.coerceAtLeast(completed))
    }

    private fun addToSession(context: Context, downloadIds: Set<String>) {
        if (downloadIds.isEmpty()) return
        val prefs = prefs(context)
        val currentIds = prefs.getStringSet(SESSION_DOWNLOAD_IDS_KEY, emptySet()) ?: emptySet()
        val addedCount = (downloadIds - currentIds).size
        if (addedCount == 0) return
        val currentTotal = prefs.getInt(SESSION_TOTAL_KEY, 0)
        prefs.edit()
            .putStringSet(SESSION_DOWNLOAD_IDS_KEY, currentIds + downloadIds)
            .putInt(SESSION_TOTAL_KEY, currentTotal + addedCount)
            .apply()
    }

    private fun removeFromSession(context: Context, downloadId: String) {
        val prefs = prefs(context)
        val sessionIds = prefs.getStringSet(SESSION_DOWNLOAD_IDS_KEY, emptySet()) ?: emptySet()
        if (downloadId !in sessionIds) return
        val completedIds = prefs.getStringSet(SESSION_COMPLETED_IDS_KEY, emptySet()) ?: emptySet()
        val wasCompleted = downloadId in completedIds
        prefs.edit()
            .putStringSet(SESSION_DOWNLOAD_IDS_KEY, sessionIds - downloadId)
            .putStringSet(SESSION_COMPLETED_IDS_KEY, completedIds - downloadId)
            .putInt(SESSION_TOTAL_KEY, (prefs.getInt(SESSION_TOTAL_KEY, 0) - 1).coerceAtLeast(0))
            .putInt(
                SESSION_COMPLETED_KEY,
                (prefs.getInt(SESSION_COMPLETED_KEY, 0) - if (wasCompleted) 1 else 0).coerceAtLeast(0),
            )
            .apply()
    }

    private fun incrementSessionCompleted(context: Context, downloadId: String) {
        val prefs = prefs(context)
        val completedIds = prefs.getStringSet(SESSION_COMPLETED_IDS_KEY, emptySet()) ?: emptySet()
        if (downloadId in completedIds) return
        val sessionIds = prefs.getStringSet(SESSION_DOWNLOAD_IDS_KEY, emptySet()) ?: emptySet()
        if (sessionIds.isNotEmpty() && downloadId !in sessionIds) return

        val currentCompleted = prefs.getInt(SESSION_COMPLETED_KEY, 0)

        prefs.edit()
            .putStringSet(SESSION_COMPLETED_IDS_KEY, completedIds + downloadId)
            .putInt(SESSION_COMPLETED_KEY, currentCompleted + 1)
            .apply()
    }

    private fun resetSessionProgress(context: Context) {
        prefs(context).edit()
            .putInt(SESSION_TOTAL_KEY, 0)
            .putInt(SESSION_COMPLETED_KEY, 0)
            .putStringSet(SESSION_COMPLETED_IDS_KEY, emptySet())
            .putStringSet(SESSION_DOWNLOAD_IDS_KEY, emptySet())
            .apply()
    }

    private fun shouldResetSessionProgress(context: Context, manager: DownloadManager): Boolean {
        if (pendingEntries(context).isNotEmpty()) return false
        if (isProcessing) return false
        return manager.currentDownloads.none { download ->
            download.state == Download.STATE_DOWNLOADING ||
            download.state == Download.STATE_QUEUED ||
            download.state == Download.STATE_RESTARTING
        }
    }

    private fun downloadId(sourceId: String, episodeId: String): String =
        "$sourceId:$episodeId"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private data class PendingEpisode(
        val sourceId: String,
        val sourceTitle: String,
        val sourceEpisodeCount: Int?,
        val sourceQualityLabel: String?,
        val sourceIsPriority: Boolean,
        val episodeId: String,
        val episodeNumber: Double,
        val episodeTitle: String?,
        val requestToken: String = UUID.randomUUID().toString(),
    ) {
        val downloadId: String = downloadId(sourceId, episodeId)

        fun toWatchSource(): WatchSource {
            return WatchSource(
                sourceId = sourceId,
                title = sourceTitle,
                episodeCount = sourceEpisodeCount,
                qualityLabel = sourceQualityLabel,
                isPriority = sourceIsPriority,
            )
        }

        fun toWatchEpisode(): WatchEpisode {
            return WatchEpisode(
                id = episodeId,
                number = episodeNumber,
                title = episodeTitle,
            )
        }

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("sourceId", sourceId)
                put("sourceTitle", sourceTitle)
                put("sourceEpisodeCount", sourceEpisodeCount)
                put("sourceQualityLabel", sourceQualityLabel)
                put("sourceIsPriority", sourceIsPriority)
                put("episodeId", episodeId)
                put("episodeNumber", episodeNumber)
                put("episodeTitle", episodeTitle)
                put("requestToken", requestToken)
            }
        }

        companion object {
            fun fromJson(json: JSONObject): PendingEpisode? {
                val sourceId = json.optString("sourceId").takeIf(String::isNotBlank) ?: return null
                val episodeId = json.optString("episodeId").takeIf(String::isNotBlank) ?: return null
                return PendingEpisode(
                    sourceId = sourceId,
                    sourceTitle = json.optString("sourceTitle").ifBlank { "Озвучка" },
                    sourceEpisodeCount = json.optInt("sourceEpisodeCount", -1).takeIf { it >= 0 },
                    sourceQualityLabel = json.optString("sourceQualityLabel").ifBlank { null },
                    sourceIsPriority = json.optBoolean("sourceIsPriority", false),
                    episodeId = episodeId,
                    episodeNumber = json.optDouble("episodeNumber", 0.0),
                    episodeTitle = json.optString("episodeTitle").ifBlank { null },
                    requestToken = json.optString("requestToken")
                        .ifBlank { "$sourceId:$episodeId" },
                )
            }
        }
    }
}

sealed interface OfflineEpisodeDownloadState {
    data object NotDownloaded : OfflineEpisodeDownloadState
    data object Queued : OfflineEpisodeDownloadState
    data class Downloading(val progress: Float) : OfflineEpisodeDownloadState
    data object Paused : OfflineEpisodeDownloadState
    data object Completed : OfflineEpisodeDownloadState
    data object Failed : OfflineEpisodeDownloadState
}

data class DownloadNotificationMeta(
    val animeTitle: String?,
    val sourceTitle: String,
    val episodeId: String,
    val episodeNumber: Double,
    val episodeTitle: String?,
) {
    val displayTitle: String
        get() = animeTitle?.takeIf(String::isNotBlank) ?: sourceTitle
}

fun parseDownloadNotificationMeta(data: ByteArray): DownloadNotificationMeta? {
    return runCatching {
        val json = org.json.JSONObject(String(data, Charsets.UTF_8))
        DownloadNotificationMeta(
            animeTitle = json.optString("animeTitle").ifBlank { null },
            sourceTitle = json.optString("sourceTitle").ifBlank { "Озвучка" },
            episodeId = json.optString("episodeId").orEmpty(),
            episodeNumber = json.optDouble("episodeNumber", 0.0),
            episodeTitle = json.optString("episodeTitle").ifBlank { null },
        )
    }.getOrNull()
}
