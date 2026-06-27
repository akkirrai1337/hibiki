package org.akkirrai.hibiki.core.source

import android.content.Context
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
            autoSelect = readBoolean(titleId, ::selectedAutoKey, true),
        )
    }

    fun saveSelectedSource(
        titleId: String,
        sourceId: String?,
        sourceTitle: String?,
        quality: String?,
        autoSelect: Boolean,
    ) {
        val normalizedTitleId = YummyIdMigration.normalizeTitleId(titleId)
        prefs.edit()
            .removeSelectionKeys(titleId)
            .putString(selectedSourceKey(normalizedTitleId), sourceId)
            .putString(selectedSourceTitleKey(normalizedTitleId), sourceTitle)
            .putString(selectedQualityKey(normalizedTitleId), quality)
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

    private companion object {
        const val PREFS_NAME = "hibiki_watch_state"
        const val PROGRESS_PREFIX = "progress_"
        const val SEPARATOR = '\u001F'
    }
}
