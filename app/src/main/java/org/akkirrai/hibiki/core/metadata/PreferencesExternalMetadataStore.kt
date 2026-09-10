package org.akkirrai.hibiki.core.metadata

import android.content.Context
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.metadata.CachedMetadata
import org.akkirrai.beakokit.metadata.ExternalMetadata
import org.akkirrai.beakokit.metadata.ExternalMetadataStore
import org.akkirrai.beakokit.metadata.MetadataMatchRecord
import org.akkirrai.beakokit.metadata.MetadataProviderId
import org.json.JSONObject

/**
 * SharedPreferences-backed [ExternalMetadataStore] - the same persistence
 * [org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository] already uses for offline title
 * metadata, rather than a database this app does not otherwise have.
 *
 * Matches are keyed by scoped title id and provider, and kept; media is keyed by provider and that
 * provider's own id, shared across sources, and pruned oldest-first once it grows past
 * [MAX_MEDIA_ENTRIES] so a long-lived install does not accumulate every entry it ever looked at.
 */
class PreferencesExternalMetadataStore(context: Context) : ExternalMetadataStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override fun readMatch(titleId: String, provider: MetadataProviderId): MetadataMatchRecord? {
        val stored = prefs.getString(matchKey(titleId, provider), null) ?: return null
        return decodeMatch(titleId, provider, stored)
    }

    override fun readMatches(titleId: String): List<MetadataMatchRecord> =
        MetadataProviderId.entries.mapNotNull { provider -> readMatch(titleId, provider) }

    override fun writeMatch(record: MetadataMatchRecord) {
        // A manual binding is the user's own correction; the automatic matcher must not be able to
        // walk over it, and this is the single place every write passes through.
        if (!record.manual && readMatch(record.titleId, record.provider)?.manual == true) return
        val encoded = JSONObject().apply {
            put("externalId", record.externalId ?: JSONObject.NULL)
            put("confidence", record.confidencePercent ?: JSONObject.NULL)
            put("manual", record.manual)
            put("matchedAt", record.matchedAtMillis)
        }
        prefs.edit().putString(matchKey(record.titleId, record.provider), encoded.toString()).apply()
    }

    override fun clearMatches(titleId: String) {
        val editor = prefs.edit()
        MetadataProviderId.entries.forEach { provider -> editor.remove(matchKey(titleId, provider)) }
        editor.apply()
    }

    override fun readMedia(provider: MetadataProviderId, externalId: Int): CachedMetadata? {
        val stored = prefs.getString(mediaKey(provider, externalId), null) ?: return null
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
        prefs.edit().putString(mediaKey(media.provider, media.externalId), encoded.toString()).apply()
        pruneMediaIfNeeded()
    }

    private fun decodeMatch(titleId: String, provider: MetadataProviderId, stored: String): MetadataMatchRecord? =
        runCatching {
            val decoded = JSONObject(stored)
            MetadataMatchRecord(
                titleId = titleId,
                provider = provider,
                externalId = if (decoded.isNull("externalId")) null else decoded.getInt("externalId"),
                confidencePercent = if (decoded.isNull("confidence")) null else decoded.getInt("confidence"),
                manual = decoded.optBoolean("manual", false),
                matchedAtMillis = decoded.optLong("matchedAt"),
            )
        }.getOrNull()

    private fun pruneMediaIfNeeded() {
        val mediaEntries = prefs.all.keys.filter { it.startsWith(MEDIA_PREFIX) }
        if (mediaEntries.size <= MAX_MEDIA_ENTRIES) return
        val oldestFirst = mediaEntries.map { key -> key to cachedAtOf(key) }.sortedBy { (_, cachedAt) -> cachedAt }
        val editor = prefs.edit()
        oldestFirst.take(mediaEntries.size - MAX_MEDIA_ENTRIES).forEach { (key, _) -> editor.remove(key) }
        editor.apply()
    }

    private fun cachedAtOf(key: String): Long =
        runCatching { JSONObject(prefs.getString(key, "{}").orEmpty()).optLong("cachedAt") }.getOrDefault(0L)

    private fun matchKey(titleId: String, provider: MetadataProviderId): String = "$MATCH_PREFIX${provider.id}_$titleId"

    private fun mediaKey(provider: MetadataProviderId, externalId: Int): String = "$MEDIA_PREFIX${provider.id}_$externalId"

    companion object {
        const val PREFS_NAME = "hibiki_external_metadata"
        private const val MATCH_PREFIX = "match_"
        private const val MEDIA_PREFIX = "media_"
        private const val MAX_MEDIA_ENTRIES = 500
    }
}
