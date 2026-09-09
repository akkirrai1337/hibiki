package org.akkirrai.hibiki.core.source

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.model.isEpisodeWatched
import org.akkirrai.hibiki.core.model.TitleWatchState
import org.akkirrai.hibiki.core.model.WatchSourceSelection

private const val PROGRESS_PREFIX = "progress_"
private const val EPISODE_KEY_SEPARATOR = "|episode|"
private const val SOURCE_KEY_SEPARATOR = "|source|"

internal data class ProgressStorageKey(
    val titleId: String,
    val episodeId: String,
    val sourceId: String? = null,
)

internal fun parseProgressStorageKey(key: String): ProgressStorageKey? {
    if (!key.startsWith(PROGRESS_PREFIX)) return null
    val payload = key.removePrefix(PROGRESS_PREFIX)
    val (titleId, episodeId, sourceId) = when {
        EPISODE_KEY_SEPARATOR in payload -> {
            val encodedEpisode = payload.substringAfter(EPISODE_KEY_SEPARATOR)
            Triple(
                payload.substringBefore(EPISODE_KEY_SEPARATOR),
                encodedEpisode.substringBefore(SOURCE_KEY_SEPARATOR),
                encodedEpisode.substringAfter(SOURCE_KEY_SEPARATOR, missingDelimiterValue = "").ifBlank { null },
            )
        }
        payload.startsWith("source:") && ':' in payload ->
            Triple(payload.substringBeforeLast(':'), payload.substringAfterLast(':'), null)
        ':' in payload -> Triple(payload.substringBefore(':'), payload.substringAfter(':'), null)
        else -> return null
    }
    if (titleId.isBlank() || episodeId.isBlank()) return null
    return ProgressStorageKey(
        titleId = YummyIdMigration.normalizeTitleId(titleId),
        episodeId = episodeId,
        sourceId = sourceId,
    )
}

class WatchStateRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The stored progress entries, and only those.
     *
     * Every lookup here used to go through `prefs.all`, which hands back a copy of the *entire*
     * preferences file - every episode of every title ever watched, plus every selection key -
     * before filtering it down to the one title, or the one episode, being asked about. And the
     * asking is not rare: resuming an episode does several of these, and each 30-second save
     * during playback reads the previous entry back before writing.
     *
     * So the progress entries are read out once and kept, and every write through this class drops
     * them. What survives is the filtering itself, prefix rules and legacy key shapes included -
     * this changes what those rules run against, not what they decide. The cache lives with the
     * SharedPreferences object rather than with an instance because Android hands the same one to
     * every caller of getSharedPreferences, and this class is constructed in several places.
     */
    private fun progressEntries(): Map<String, String> = synchronized(progressCacheLock) {
        cachedProgressEntries ?: prefs.all
            .asSequence()
            .filter { (key, value) -> key.startsWith(PROGRESS_PREFIX) && value is String }
            .associate { (key, value) -> key to value as String }
            .also { cachedProgressEntries = it }
    }

    private fun invalidateProgressEntries() = synchronized(progressCacheLock) { cachedProgressEntries = null }

    fun getSelectedSource(titleId: String): WatchSourceSelection {
        val normalizedTitleId = YummyIdMigration.normalizeTitleId(titleId)
        return WatchSourceSelection(
            titleId = normalizedTitleId,
            sourceId = readString(titleId, ::selectedSourceKey),
            sourceTitle = readString(titleId, ::selectedSourceTitleKey),
            quality = readString(titleId, ::selectedQualityKey),
            playerName = readString(titleId, ::selectedPlayerKey),
            autoSelect = readBoolean(titleId, ::selectedAutoKey, true),
        )
    }

    fun saveSelectedSource(
        titleId: String,
        sourceId: String?,
        sourceTitle: String?,
        quality: String?,
        playerName: String? = null,
        autoSelect: Boolean,
    ) {
        val normalizedTitleId = YummyIdMigration.normalizeTitleId(titleId)
        prefs.edit()
            .removeSelectionKeys(titleId)
            .putString(selectedSourceKey(normalizedTitleId), sourceId)
            .putString(selectedSourceTitleKey(normalizedTitleId), sourceTitle)
            .putString(selectedQualityKey(normalizedTitleId), quality)
            .putString(selectedPlayerKey(normalizedTitleId), playerName)
            .remove(selectedBackendKey(normalizedTitleId))
            .putBoolean(selectedAutoKey(normalizedTitleId), autoSelect)
            .apply()
        invalidateProgressEntries()
    }

    fun getSelectedPlayer(titleId: String, sourceId: String): String? {
        val scopedPlayer = legacyCompatibleTitleIds(titleId)
            .firstNotNullOfOrNull { candidateId ->
                prefs.getString(selectedPlayerForSourceKey(candidateId, sourceId), null)
            }
        if (scopedPlayer != null) return scopedPlayer

        // Preserve the old title-wide choice for the voiceover it originally belonged to.
        return getSelectedSource(titleId)
            .takeIf { it.sourceId == sourceId }
            ?.playerName
    }

    fun saveSelectedPlayer(titleId: String, sourceId: String, playerName: String?) {
        val normalizedTitleId = YummyIdMigration.normalizeTitleId(titleId)
        prefs.edit().apply {
            legacyCompatibleTitleIds(titleId).forEach { candidateId ->
                remove(selectedPlayerForSourceKey(candidateId, sourceId))
            }
            putString(selectedPlayerForSourceKey(normalizedTitleId, sourceId), playerName)
        }.apply()
        invalidateProgressEntries()
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
        return progressEntries().entries
            .asSequence()
            .mapNotNull { (key, value) ->
                val progressKey = parseProgressStorageKey(key) ?: return@mapNotNull null
                parseProgress(
                    titleId = progressKey.titleId,
                    episodeId = progressKey.episodeId,
                    encoded = value,
                )
            }
            .groupBy { Triple(it.titleId, it.episodeId, it.sourceId) }
            .mapNotNull { (_, items) -> items.maxByOrNull(EpisodeWatchProgress::updatedAt) }
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
        return progressEntries().entries
            .filter { (key, _) -> prefixes.any(key::startsWith) }
            .mapNotNull { (key, value) ->
                val progressKey = parseProgressStorageKey(key) ?: return@mapNotNull null
                parseProgress(
                    titleId = normalizedTitleId,
                    episodeId = progressKey.episodeId,
                    encoded = value,
                )
            }
            .groupBy { it.episodeId to it.sourceId }
            .map { (_, items) -> items.maxByOrNull(EpisodeWatchProgress::updatedAt) ?: return@map null }
            .filterNotNull()
            .sortedBy(EpisodeWatchProgress::episodeNumber)
    }

    fun getEpisodeProgressForSource(
        titleId: String,
        sourceId: String,
    ): List<EpisodeWatchProgress> = getEpisodeProgress(titleId)
        .filter { it.sourceId == sourceId }

    fun getEpisodeProgress(
        titleId: String,
        episodeId: String,
    ): EpisodeWatchProgress? {
        return getEpisodeProgress(titleId)
            .filter { it.episodeId == episodeId }
            .maxByOrNull(EpisodeWatchProgress::updatedAt)
    }

    fun getEpisodeProgress(
        titleId: String,
        episodeId: String,
        sourceId: String,
    ): EpisodeWatchProgress? {
        return getEpisodeProgressForSource(titleId, sourceId)
            .filter { it.episodeId == episodeId }
            .maxByOrNull(EpisodeWatchProgress::updatedAt)
    }

    /** Moves progress written by the old scoped-id truncation bug to its real title key. */
    fun migrateLegacyScopedEpisodeProgress(
        titleId: String,
        episodeIds: Set<String>,
    ) {
        val normalizedTitleId = YummyIdMigration.normalizeTitleId(titleId)
        if (!normalizedTitleId.startsWith("source:") || episodeIds.isEmpty()) return
        val legacyTitleId = normalizedTitleId.substringBefore(':')
        val candidates = getEpisodeProgress(legacyTitleId).filter { progress ->
            progress.episodeId in episodeIds &&
                watchTitleIdFromSourceId(progress.sourceId) == normalizedTitleId &&
                getEpisodeProgress(normalizedTitleId, progress.episodeId, progress.sourceId) == null
        }
        if (candidates.isEmpty()) return
        prefs.edit().apply {
            candidates.forEach { progress ->
                val encoded = episodeProgressKeys(legacyTitleId, progress.episodeId)
                    .firstNotNullOfOrNull { key -> prefs.getString(key, null) }
                    ?: return@forEach
                putString(progressKey(normalizedTitleId, progress.episodeId, progress.sourceId), encoded)
                episodeProgressKeys(legacyTitleId, progress.episodeId).forEach(::remove)
            }
        }.apply()
        invalidateProgressEntries()
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
        // How much of this save actually played, when the caller knows. The player does: it counts
        // the seconds it saw play, so a seek across half a film adds nothing. Callers that only
        // move the position (marking an episode watched from the list) leave it null and fall back
        // to the distance moved.
        watchedMsDelta: Long? = null,
    ) {
        val normalizedTitleId = YummyIdMigration.normalizeTitleId(titleId)
        val previous = getEpisodeProgress(titleId, episodeId, sourceId)
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
            .putString(progressKey(normalizedTitleId, episodeId, sourceId), encoded)
            .apply()
        invalidateProgressEntries()

        recordActivity(
            titleId = normalizedTitleId,
            episodeId = episodeId,
            previousPositionMs = previous?.positionMs ?: 0L,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = updatedAt,
            watchedMsDelta = watchedMsDelta,
        )
    }

    fun clearEpisodeProgress(
        titleId: String,
        episodeId: String,
        sourceId: String,
    ) {
        val legacyKeysForSource = episodeProgressKeys(titleId, episodeId).filter { key ->
            prefs.getString(key, null)?.let { encoded ->
                parseProgress(YummyIdMigration.normalizeTitleId(titleId), episodeId, encoded)?.sourceId == sourceId
            } == true
        }
        prefs.edit().apply {
            legacyCompatibleTitleIds(titleId).forEach { candidateId ->
                remove(progressKey(candidateId, episodeId, sourceId))
            }
            legacyKeysForSource.forEach(::remove)
        }.apply()
        invalidateProgressEntries()
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

    private fun progressKey(titleId: String, episodeId: String, sourceId: String? = null): String =
        buildString {
            append(canonicalEpisodePrefix(titleId))
            append(episodeId)
            if (sourceId != null) append(SOURCE_KEY_SEPARATOR).append(sourceId)
        }

    private fun canonicalEpisodePrefix(titleId: String): String =
        "$PROGRESS_PREFIX$titleId$EPISODE_KEY_SEPARATOR"

    private fun legacyEpisodePrefix(titleId: String): String = "$PROGRESS_PREFIX$titleId:"

    private fun selectedSourceKey(titleId: String): String = "selected_source_$titleId"

    private fun selectedSourceTitleKey(titleId: String): String = "selected_source_title_$titleId"

    private fun selectedQualityKey(titleId: String): String = "selected_source_quality_$titleId"

    private fun selectedPlayerKey(titleId: String): String = "selected_player_$titleId"

    private fun selectedPlayerForSourceKey(titleId: String, sourceId: String): String =
        "selected_player_for_source_${titleId}_$sourceId"

    private fun selectedBackendKey(titleId: String): String = "selected_backend_$titleId"

    private fun selectedAutoKey(titleId: String): String = "selected_source_auto_$titleId"

    private fun normalizedTitleId(titleId: String): String = YummyIdMigration.normalizeTitleId(titleId)

    private fun legacyCompatibleTitleIds(titleId: String): List<String> {
        return YummyIdMigration.compatibleTitleIds(titleId)
    }

    private fun episodePrefixes(titleId: String): List<String> {
        return legacyCompatibleTitleIds(titleId).flatMap { candidateId ->
            listOf(canonicalEpisodePrefix(candidateId), legacyEpisodePrefix(candidateId))
        }
    }

    private fun episodeProgressKeys(titleId: String, episodeId: String): List<String> {
        return legacyCompatibleTitleIds(titleId).flatMap { candidateId ->
            listOf(
                progressKey(candidateId, episodeId),
                "${legacyEpisodePrefix(candidateId)}$episodeId",
            )
        }
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
        private val progressCacheLock = Any()

        @Volatile
        private var cachedProgressEntries: Map<String, String>? = null

        const val PREFS_NAME = "hibiki_watch_state"
        // Ten years, which is to say "keep it". The profile's watch-time total and its best
        // streak are lifetime figures on the desktop, and a 90-day window quietly turned both
        // into three-month figures here: an hour watched in spring stopped counting in summer,
        // and a month-long streak expired instead of standing. One long per day is nothing to
        // store.
        const val ACTIVITY_RETENTION_DAYS = 3650
        private const val SEPARATOR = '\u001F'
        private const val ACTIVITY_WATCHED_PREFIX = "activity_watched_"
        private const val ACTIVITY_COMPLETED_PREFIX = "activity_completed_"
    }

    /**
     * Returns every locally recorded episode progress entry. The entries are keyed by
     * title and episode, so this remains independent of the streaming source used to
     * play an episode.
     */
    fun getAllEpisodeProgress(): List<EpisodeWatchProgress> {
        return progressEntries().entries
            .asSequence()
            .mapNotNull { (key, value) ->
                val progressKey = parseProgressStorageKey(key) ?: return@mapNotNull null
                parseProgress(
                    titleId = progressKey.titleId,
                    episodeId = progressKey.episodeId,
                    encoded = value,
                )
            }
            .groupBy { Triple(it.titleId, it.episodeId, it.sourceId) }
            .mapNotNull { (_, items) -> items.maxByOrNull(EpisodeWatchProgress::updatedAt) }
            .toList()
    }

    /** Daily local playback aggregates used by the profile. Resume state remains separate. */
    fun getDailyWatchActivity(): List<DailyWatchActivity> {
        pruneActivityBefore(activityCutoffDate())
        return prefs.all.keys
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
    }

    private fun recordActivity(
        titleId: String,
        episodeId: String,
        previousPositionMs: Long,
        positionMs: Long,
        durationMs: Long,
        updatedAt: Long,
        watchedMsDelta: Long?,
    ) {
        // The distance the position moved is only a stand-in for watched time, and a poor one: a
        // single seek to the end of a film used to book the whole film as watched. Real playback
        // time wins whenever the caller measured it.
        val deltaMs = (watchedMsDelta ?: (positionMs - previousPositionMs))
            .coerceAtLeast(0L)
            .coerceAtMost(durationMs.coerceAtLeast(0L))
        val completed = isEpisodeWatched(positionMs, durationMs)
        if (deltaMs == 0L && !completed) return

        val date = Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val cutoffDate = activityCutoffDate()
        pruneActivityBefore(cutoffDate)
        if (date.isBefore(cutoffDate)) return
        val completedKey = completedActivityKey(date)
        val completedEpisodes = prefs.getStringSet(completedKey, emptySet()).orEmpty().toMutableSet()
        if (completed) completedEpisodes += "$titleId:$episodeId"
        prefs.edit()
            .putLong(activityWatchedKey(date), prefs.getLong(activityWatchedKey(date), 0L) + deltaMs)
            .putStringSet(completedKey, completedEpisodes)
            .apply()
        invalidateProgressEntries()
    }

    private fun activityWatchedKey(date: LocalDate): String = "$ACTIVITY_WATCHED_PREFIX$date"

    private fun completedActivityKey(date: LocalDate): String = "$ACTIVITY_COMPLETED_PREFIX$date"

    private fun activityCutoffDate(): LocalDate =
        LocalDate.now().minusDays((ACTIVITY_RETENTION_DAYS - 1).toLong())

    private fun pruneActivityBefore(cutoffDate: LocalDate) {
        val expiredKeys = prefs.all.keys.filter { key ->
            val rawDate = when {
                key.startsWith(ACTIVITY_WATCHED_PREFIX) -> key.removePrefix(ACTIVITY_WATCHED_PREFIX)
                key.startsWith(ACTIVITY_COMPLETED_PREFIX) -> key.removePrefix(ACTIVITY_COMPLETED_PREFIX)
                else -> return@filter false
            }
            runCatching { LocalDate.parse(rawDate) }
                .getOrNull()
                ?.isBefore(cutoffDate) == true
        }
        if (expiredKeys.isEmpty()) return
        prefs.edit().apply {
            expiredKeys.forEach(::remove)
        }.apply()
        invalidateProgressEntries()
    }

    data class DailyWatchActivity(
        val date: LocalDate,
        val watchedMs: Long,
        val completedEpisodes: Int,
    )

}
