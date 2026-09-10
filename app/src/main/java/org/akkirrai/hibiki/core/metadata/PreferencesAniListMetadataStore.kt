package org.akkirrai.hibiki.core.metadata

import android.content.Context
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.metadata.AniListMetadataStore
import org.akkirrai.beakokit.metadata.CachedMetadata
import org.akkirrai.beakokit.metadata.ExternalMetadata
import org.akkirrai.beakokit.metadata.MetadataMatchRecord
import org.json.JSONObject

/**
 * SharedPreferences-backed [AniListMetadataStore] - the same persistence
 * [org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository] already uses for offline title
 * metadata, rather than a database this app does not otherwise have.
 *
 * Matches are keyed by scoped title id and kept indefinitely; media is keyed by AniList id, shared
 * across sources, and pruned oldest-first once it grows past [MAX_MEDIA_ENTRIES] so a long-lived
 * install does not accumulate every entry it ever looked at.
 */
class PreferencesAniListMetadataStore(context: Context) : AniListMetadataStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override fun readMatch(titleId: String): MetadataMatchRecord? {
        val stored = prefs.getString(matchKey(titleId), null) ?: return null
        return runCatching {
            val decoded = JSONObject(stored)
            MetadataMatchRecord(
                titleId = titleId,
                anilistId = if (decoded.isNull("anilistId")) null else decoded.getInt("anilistId"),
                confidencePercent = if (decoded.isNull("confidence")) null else decoded.getInt("confidence"),
                manual = decoded.optBoolean("manual", false),
                matchedAtMillis = decoded.optLong("matchedAt"),
            )
        }.getOrNull()
    }

    override fun writeMatch(record: MetadataMatchRecord) {
        // A manual binding is the user's own correction; the automatic matcher must not be able to
        // walk over it, and this is the single place every write passes through.
        if (!record.manual && readMatch(record.titleId)?.manual == true) return
        val encoded = JSONObject().apply {
            put("anilistId", record.anilistId ?: JSONObject.NULL)
            put("confidence", record.confidencePercent ?: JSONObject.NULL)
            put("manual", record.manual)
            put("matchedAt", record.matchedAtMillis)
        }
        prefs.edit().putString(matchKey(record.titleId), encoded.toString()).apply()
    }

    override fun clearMatch(titleId: String) {
        prefs.edit().remove(matchKey(titleId)).apply()
    }

    override fun readMedia(anilistId: Int): CachedMetadata? {
        val stored = prefs.getString(mediaKey(anilistId), null) ?: return null
        return runCatching {
            val decoded = JSONObject(stored)
            CachedMetadata(
                media = json.decodeFromString(ExternalMetadata.serializer(), decoded.getString("media")),
                cachedAtMillis = decoded.optLong("cachedAt"),
            )
        }.getOrNull()
    }

    override fun writeMedia(media: ExternalMetadata, cachedAtMillis: Long) {
        val encoded = JSONObject().apply {
            put("media", json.encodeToString(ExternalMetadata.serializer(), media))
            put("cachedAt", cachedAtMillis)
        }
        prefs.edit().putString(mediaKey(media.anilistId), encoded.toString()).apply()
        pruneMediaIfNeeded()
    }

    private fun pruneMediaIfNeeded() {
        val mediaEntries = prefs.all.keys.filter { key -> key.startsWith(MEDIA_PREFIX) }
        if (mediaEntries.size <= MAX_MEDIA_ENTRIES) return
        val oldestFirst = mediaEntries
            .map { key -> key to cachedAtOf(key) }
            .sortedBy { (_, cachedAt) -> cachedAt }
        val editor = prefs.edit()
        oldestFirst.take(mediaEntries.size - MAX_MEDIA_ENTRIES).forEach { (key, _) -> editor.remove(key) }
        editor.apply()
    }

    private fun cachedAtOf(key: String): Long = runCatching {
        JSONObject(prefs.getString(key, "{}").orEmpty()).optLong("cachedAt")
    }.getOrDefault(0L)

    private fun matchKey(titleId: String): String = "$MATCH_PREFIX$titleId"

    private fun mediaKey(anilistId: Int): String = "$MEDIA_PREFIX$anilistId"

    companion object {
        const val PREFS_NAME = "hibiki_anilist_metadata"
        private const val MATCH_PREFIX = "match_"
        private const val MEDIA_PREFIX = "media_"
        private const val MAX_MEDIA_ENTRIES = 500
    }
}
