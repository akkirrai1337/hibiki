package org.akkirrai.hibiki.core.source

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.model.TitleWatchState
import org.akkirrai.hibiki.core.model.WatchSourceSelection

class WatchStateRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedSource(titleId: String): WatchSourceSelection {
        val normalizedTitleId = YummyIdMigration.normalizeTitleId(titleId)
        return WatchSourceSelection(
            titleId = normalizedTitleId,
            sourceId = readString(titleId, ::selectedSourceKey),
            sourceTitle = readString(titleId, ::selectedSourceTitleKey),
            quality = readString(titleId, ::selectedQualityKey),
            playerName = readString(titleId, ::selectedPlayerKey),
            backendId = readString(titleId, ::selectedBackendKey),
            autoSelect = readBoolean(titleId, ::selectedAutoKey, true),
        )
    }

    fun saveSelectedSource(
        titleId: String,
        sourceId: String?,
        sourceTitle: String?,
        quality: String?,
        playerName: String? = null,
        backendId: String? = null,
        autoSelect: Boolean,
    ) {
        val normalizedTitleId = YummyIdMigration.normalizeTitleId(titleId)
        prefs.edit()
            .removeSelectionKeys(titleId)
            .putString(selectedSourceKey(normalizedTitleId), sourceId)
            .putString(selectedSourceTitleKey(normalizedTitleId), sourceTitle)
            .putString(selectedQualityKey(normalizedTitleId), quality)
            .putString(selectedPlayerKey(normalizedTitleId), playerName)
            .putString(selectedBackendKey(normalizedTitleId), backendId)
            .putBoolean(selectedAutoKey(normalizedTitleId), autoSelect)
            .apply()
    }

    fun getTitleWatchState(titleId: String): TitleWatchState? {
        val normalizedTitleId = YummyIdMigration.normalizeTitleId(titleId)
        val progressItems = getEpisodeProgress(normalizedTitleId)
        if (progressItems.isEmpty()) {
            return null
        }
        val latest = progressItems.maxByOrNull(EpisodeWatchProgress::updatedAt) ?: return null
        return TitleWatchState(
            titleId = latest.titleId,
            episodeId = latest.episodeId,
            episodeNumber = latest.episodeNumber,
            sourceId = latest.sourceId,
            voiceoverId = latest.voiceoverId,
            sourceTitle = latest.sourceTitle,
            quality = latest.quality,
            positionMs = latest.positionMs,
            durationMs = latest.durationMs,
            updatedAt = latest.updatedAt,
        )
    }

    fun getRecentTitleWatchState(): TitleWatchState? {
        return prefs.all.entries
            .asSequence()
            .filter { (key, value) ->
                key.startsWith(PROGRESS_PREFIX) && value is String
            }
            .mapNotNull { (key, value) ->
                val titleId = key.substringAfter(PROGRESS_PREFIX).substringBefore(':')
                val episodeId = key.substringAfter(':', missingDelimiterValue = "")
                if (titleId.isBlank() || episodeId.isBlank()) {
                    return@mapNotNull null
                }
                parseProgress(
                    titleId = YummyIdMigration.normalizeTitleId(titleId),
                    episodeId = episodeId,
                    encoded = value as String,
                )
            }
            .distinctBy { "${it.titleId}:${it.episodeId}" }
            .groupBy(EpisodeWatchProgress::titleId)
            .values
            .mapNotNull { items -> items.maxByOrNull(EpisodeWatchProgress::updatedAt) }
            .maxByOrNull(EpisodeWatchProgress::updatedAt)
            ?.let { latest ->
                TitleWatchState(
                    titleId = latest.titleId,
                    episodeId = latest.episodeId,
                    episodeNumber = latest.episodeNumber,
                    sourceId = latest.sourceId,
                    voiceoverId = latest.voiceoverId,
                    sourceTitle = latest.sourceTitle,
                    quality = latest.quality,
                    positionMs = latest.positionMs,
                    durationMs = latest.durationMs,
                    updatedAt = latest.updatedAt,
                )
            }
    }

    fun getEpisodeProgress(titleId: String): List<EpisodeWatchProgress> {
        val normalizedTitleId = YummyIdMigration.normalizeTitleId(titleId)
        val prefixes = episodePrefixes(titleId)
        return prefs.all.entries
            .filter { (key, value) ->
                prefixes.any(key::startsWith) && value is String
            }
            .mapNotNull { (key, value) ->
                parseProgress(
                    titleId = normalizedTitleId,
                    episodeId = prefixes.firstNotNullOfOrNull { prefix ->
                        key.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
                    } ?: return@mapNotNull null,
                    encoded = value as String,
                )
            }
            .distinctBy(EpisodeWatchProgress::episodeId)
            .sortedBy(EpisodeWatchProgress::episodeNumber)
    }

    fun getEpisodeProgress(
        titleId: String,
        episodeId: String,
    ): EpisodeWatchProgress? {
        val normalizedTitleId = YummyIdMigration.normalizeTitleId(titleId)
        val value = episodeProgressKeys(titleId, episodeId)
            .firstNotNullOfOrNull { key -> prefs.getString(key, null) }
            ?: return null
        return parseProgress(normalizedTitleId, episodeId, value)
    }

    fun saveEpisodeProgress(
        titleId: String,
        episodeId: String,
        episodeNumber: Double,
        sourceId: String,
        voiceoverId: String,
        sourceTitle: String,
        quality: String?,
        positionMs: Long,
        durationMs: Long,
        updatedAt: Long = System.currentTimeMillis(),
    ) {
        val normalizedTitleId = YummyIdMigration.normalizeTitleId(titleId)
        val previous = getEpisodeProgress(titleId, episodeId)
        val encoded = listOf(
            episodeNumber.toString(),
            sourceId,
            voiceoverId,
            sourceTitle,
            quality.orEmpty(),
            positionMs.toString(),
            durationMs.toString(),
            updatedAt.toString(),
        ).joinToString(SEPARATOR.toString())

        prefs.edit()
            .removeLegacyProgressEntries(titleId, episodeId)
            .putString(progressKey(normalizedTitleId, episodeId), encoded)
            .apply()

        recordActivity(
            titleId = normalizedTitleId,
            episodeId = episodeId,
            previousPositionMs = previous?.positionMs ?: 0L,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = updatedAt,
        )
    }

    private fun parseProgress(
        titleId: String,
        episodeId: String,
        encoded: String,
    ): EpisodeWatchProgress? {
        val parts = encoded.split(SEPARATOR)
        if (parts.size < 8) {
            return null
        }
        return EpisodeWatchProgress(
            titleId = YummyIdMigration.normalizeTitleId(titleId),
            episodeId = episodeId,
            episodeNumber = parts[0].toDoubleOrNull() ?: return null,
            sourceId = parts[1],
            voiceoverId = parts[2],
            sourceTitle = parts[3],
            quality = parts[4].ifBlank { null },
            positionMs = parts[5].toLongOrNull() ?: 0L,
            durationMs = parts[6].toLongOrNull() ?: 0L,
            updatedAt = parts[7].toLongOrNull() ?: 0L,
        )
    }

    private fun progressKey(titleId: String, episodeId: String): String =
        "${episodePrefix(titleId)}$episodeId"

    private fun episodePrefix(titleId: String): String = "progress_$titleId:"

    private fun selectedSourceKey(titleId: String): String = "selected_source_$titleId"

    private fun selectedSourceTitleKey(titleId: String): String = "selected_source_title_$titleId"

    private fun selectedQualityKey(titleId: String): String = "selected_source_quality_$titleId"

    private fun selectedPlayerKey(titleId: String): String = "selected_player_$titleId"

    private fun selectedBackendKey(titleId: String): String = "selected_backend_$titleId"

    private fun selectedAutoKey(titleId: String): String = "selected_source_auto_$titleId"

    private fun normalizedTitleId(titleId: String): String = YummyIdMigration.normalizeTitleId(titleId)

    private fun legacyCompatibleTitleIds(titleId: String): List<String> {
        return listOf(normalizedTitleId(titleId), titleId).distinct()
    }

    private fun episodePrefixes(titleId: String): List<String> {
        return legacyCompatibleTitleIds(titleId).map(::episodePrefix)
    }

    private fun episodeProgressKeys(titleId: String, episodeId: String): List<String> {
        return legacyCompatibleTitleIds(titleId).map { progressKey(it, episodeId) }
    }

    private fun readString(titleId: String, keyBuilder: (String) -> String): String? {
        return legacyCompatibleTitleIds(titleId)
            .firstNotNullOfOrNull { candidateId -> prefs.getString(keyBuilder(candidateId), null) }
    }

    private fun readBoolean(
        titleId: String,
        keyBuilder: (String) -> String,
        defaultValue: Boolean,
    ): Boolean {
        val keys = legacyCompatibleTitleIds(titleId).map(keyBuilder)
        return keys.firstOrNull(prefs::contains)?.let { key -> prefs.getBoolean(key, defaultValue) }
            ?: defaultValue
    }

    private fun android.content.SharedPreferences.Editor.removeSelectionKeys(titleId: String):
        android.content.SharedPreferences.Editor {
        legacyCompatibleTitleIds(titleId).forEach { candidateId ->
            remove(selectedSourceKey(candidateId))
            remove(selectedSourceTitleKey(candidateId))
            remove(selectedQualityKey(candidateId))
            remove(selectedPlayerKey(candidateId))
            remove(selectedBackendKey(candidateId))
            remove(selectedAutoKey(candidateId))
        }
        return this
    }

    private fun android.content.SharedPreferences.Editor.removeLegacyProgressEntries(
        titleId: String,
        episodeId: String,
    ): android.content.SharedPreferences.Editor {
        episodeProgressKeys(titleId, episodeId).forEach(::remove)
        return this
    }

    companion object {
        const val PREFS_NAME = "hibiki_watch_state"
        private const val PROGRESS_PREFIX = "progress_"
        private const val SEPARATOR = '\u001F'
        private const val ACTIVITY_WATCHED_PREFIX = "activity_watched_"
        private const val ACTIVITY_COMPLETED_PREFIX = "activity_completed_"
        private const val COMPLETION_THRESHOLD_PERCENT = 90L
    }

    /**
     * Returns every locally recorded episode progress entry. The entries are keyed by
     * title and episode, so this remains independent of the streaming source used to
     * play an episode.
     */
    fun getAllEpisodeProgress(): List<EpisodeWatchProgress> {
        return prefs.all.entries
            .asSequence()
            .filter { (key, value) -> key.startsWith(PROGRESS_PREFIX) && value is String }
            .mapNotNull { (key, value) ->
                val titleId = key.substringAfter(PROGRESS_PREFIX).substringBefore(':')
                val episodeId = key.substringAfter(':', missingDelimiterValue = "")
                if (titleId.isBlank() || episodeId.isBlank()) null else parseProgress(
                    titleId = YummyIdMigration.normalizeTitleId(titleId),
                    episodeId = episodeId,
                    encoded = value as String,
                )
            }
            .distinctBy { "${it.titleId}:${it.episodeId}" }
            .toList()
    }

    /** Daily local playback aggregates used by the profile. Resume state remains separate. */
    fun getDailyWatchActivity(): List<DailyWatchActivity> = prefs.all.keys
        .asSequence()
        .filter { it.startsWith(ACTIVITY_WATCHED_PREFIX) }
        .mapNotNull { key ->
            val date = runCatching { LocalDate.parse(key.removePrefix(ACTIVITY_WATCHED_PREFIX)) }.getOrNull()
                ?: return@mapNotNull null
            val watchedMs = prefs.getLong(key, 0L).coerceAtLeast(0L)
            val completed = prefs.getStringSet(completedActivityKey(date), emptySet()).orEmpty().size
            DailyWatchActivity(date = date, watchedMs = watchedMs, completedEpisodes = completed)
        }
        .sortedBy(DailyWatchActivity::date)
        .toList()

    private fun recordActivity(
        titleId: String,
        episodeId: String,
        previousPositionMs: Long,
        positionMs: Long,
        durationMs: Long,
        updatedAt: Long,
    ) {
        val deltaMs = (positionMs - previousPositionMs)
            .coerceAtLeast(0L)
            .coerceAtMost(durationMs.coerceAtLeast(0L))
        val completed = durationMs > 0L && positionMs >= durationMs * COMPLETION_THRESHOLD_PERCENT / 100L
        if (deltaMs == 0L && !completed) return

        val date = Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val completedKey = completedActivityKey(date)
        val completedEpisodes = prefs.getStringSet(completedKey, emptySet()).orEmpty().toMutableSet()
        if (completed) completedEpisodes += "$titleId:$episodeId"
        prefs.edit()
            .putLong(activityWatchedKey(date), prefs.getLong(activityWatchedKey(date), 0L) + deltaMs)
            .putStringSet(completedKey, completedEpisodes)
            .apply()
    }

    private fun activityWatchedKey(date: LocalDate): String = "$ACTIVITY_WATCHED_PREFIX$date"

    private fun completedActivityKey(date: LocalDate): String = "$ACTIVITY_COMPLETED_PREFIX$date"

    data class DailyWatchActivity(
        val date: LocalDate,
        val watchedMs: Long,
        val completedEpisodes: Int,
    )

}
