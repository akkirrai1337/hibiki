package org.akkirrai.hibiki.core.metadata

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.LruCache
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.metadata.CachedMetadata
import org.akkirrai.beakokit.metadata.ExternalMetadata
import org.akkirrai.beakokit.metadata.ExternalMetadataStore
import org.akkirrai.beakokit.metadata.MetadataMatchRecord
import org.akkirrai.beakokit.metadata.MetadataMediaKey
import org.akkirrai.beakokit.metadata.MetadataProviderId
import org.akkirrai.hibiki.core.database.HibikiDatabase
import org.akkirrai.hibiki.core.log.AppLogger
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Room-backed [ExternalMetadataStore]: one row per write, an indexed reverse lookup for opening an
 * aggregator card, and a small memory cache of decoded media, since a list screen reads the same
 * entries on every render.
 */
class RoomExternalMetadataStore private constructor(context: Context) : ExternalMetadataStore {
    private val dao = HibikiDatabase.get(context).metadataDao()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mediaMemory = LruCache<String, CachedMetadata>(MEDIA_MEMORY_ENTRIES)
    private val mediaWrites = AtomicInteger()

    override fun readMatch(titleId: String, provider: MetadataProviderId): MetadataMatchRecord? =
        dao.match(titleId, provider.id)?.toRecord()

    override fun readMatches(titleId: String): List<MetadataMatchRecord> =
        dao.matches(titleId).mapNotNull { it.toRecord() }

    override fun readMatches(titleIds: List<String>): Map<String, List<MetadataMatchRecord>> {
        if (titleIds.isEmpty()) return emptyMap()
        return dao.matches(titleIds.distinct())
            .mapNotNull { entity -> entity.toRecord()?.let { entity.titleId to it } }
            .groupBy({ it.first }, { it.second })
    }

    override fun writeMatch(record: MetadataMatchRecord) = dao.writeMatch(record.toEntity())

    override fun clearMatches(titleId: String) = dao.clearTitle(titleId)

    override fun readDisplayProvider(titleId: String): MetadataProviderId? =
        dao.displayProvider(titleId)?.let(MetadataProviderId::fromId)

    override fun readDisplayProviders(titleIds: List<String>): Map<String, MetadataProviderId> {
        if (titleIds.isEmpty()) return emptyMap()
        return dao.displayProviders(titleIds.distinct()).mapNotNull { entry ->
            MetadataProviderId.fromId(entry.provider)?.let { entry.titleId to it }
        }.toMap()
    }

    override fun writeDisplayProvider(titleId: String, provider: MetadataProviderId) =
        dao.upsertDisplayProvider(MetadataDisplayProviderEntity(titleId, provider.id))

    override fun matchesForEntry(sourceId: String, provider: MetadataProviderId, externalId: Int): List<MetadataMatchRecord> {
        // Filtered here rather than with LIKE: source ids may contain '_', LIKE's single-character wildcard.
        val prefix = "$sourceId:"
        return dao.matchesForEntry(provider.id, externalId)
            .filter { it.titleId.startsWith(prefix) }
            .mapNotNull { it.toRecord() }
    }

    override fun readUnresolvedAt(sourceId: String, provider: MetadataProviderId, externalId: Int): Long? =
        dao.unresolvedAt(sourceId, provider.id, externalId)

    override fun writeUnresolved(sourceId: String, provider: MetadataProviderId, externalId: Int, attemptedAtMillis: Long) =
        dao.upsertUnresolved(MetadataUnresolvedEntity(sourceId, provider.id, externalId, attemptedAtMillis))

    override fun clearUnresolved(sourceId: String, provider: MetadataProviderId, externalId: Int) =
        dao.deleteUnresolved(sourceId, provider.id, externalId)

    override fun readMedia(provider: MetadataProviderId, externalId: Int): CachedMetadata? {
        val key = "${provider.id}_$externalId"
        mediaMemory.get(key)?.let { return it }
        val row = dao.media(provider.id, externalId) ?: return null
        val cached = runCatching {
            CachedMetadata(json.decodeFromString(ExternalMetadata.serializer(), row.json), row.cachedAt)
        }.getOrNull() ?: return null
        mediaMemory.put(key, cached)
        return cached
    }

    override fun readMediaBatch(keys: List<MetadataMediaKey>): Map<MetadataMediaKey, CachedMetadata> {
        val distinctKeys = keys.distinct()
        if (distinctKeys.isEmpty()) return emptyMap()
        val found = mutableMapOf<MetadataMediaKey, CachedMetadata>()
        val missing = mutableListOf<MetadataMediaKey>()
        for (key in distinctKeys) {
            val cached = mediaMemory.get("${key.provider.id}_${key.externalId}")
            if (cached != null) found[key] = cached else missing.add(key)
        }
        for ((provider, providerKeys) in missing.groupBy(MetadataMediaKey::provider)) {
            val rows = dao.media(provider.id, providerKeys.map(MetadataMediaKey::externalId))
            for (row in rows) {
                val cached = runCatching {
                    CachedMetadata(json.decodeFromString(ExternalMetadata.serializer(), row.json), row.cachedAt)
                }.getOrNull() ?: continue
                val key = MetadataMediaKey(provider, row.externalId)
                mediaMemory.put("${provider.id}_${row.externalId}", cached)
                found[key] = cached
            }
        }
        return found
    }

    override fun writeMedia(media: ExternalMetadata, cachedAtMillis: Long) {
        dao.upsertMedia(
            MetadataMediaEntity(media.provider.id, media.externalId, json.encodeToString(ExternalMetadata.serializer(), media), cachedAtMillis),
        )
        mediaMemory.put("${media.provider.id}_${media.externalId}", CachedMetadata(media, cachedAtMillis))
        val totalWrites = mediaWrites.incrementAndGet()
        pruneIfNeeded(totalWrites, previousWrites = totalWrites - 1)
    }

    override fun writeMediaBatch(media: List<ExternalMetadata>, cachedAtMillis: Long) {
        if (media.isEmpty()) return
        dao.upsertMedia(
            media.map { entry ->
                MetadataMediaEntity(
                    entry.provider.id,
                    entry.externalId,
                    json.encodeToString(ExternalMetadata.serializer(), entry),
                    cachedAtMillis,
                )
            },
        )
        media.forEach { entry ->
            mediaMemory.put("${entry.provider.id}_${entry.externalId}", CachedMetadata(entry, cachedAtMillis))
        }
        val totalWrites = mediaWrites.addAndGet(media.size)
        pruneIfNeeded(totalWrites, previousWrites = totalWrites - media.size)
    }

    private fun pruneIfNeeded(totalWrites: Int, previousWrites: Int) {
        if (totalWrites / PRUNE_EVERY_WRITES > previousWrites / PRUNE_EVERY_WRITES) {
            dao.prune(MAX_MEDIA_ENTRIES, System.currentTimeMillis() - FAILURE_RECORD_RETENTION_MILLIS)
        }
    }

    private fun MetadataMatchEntity.toRecord(): MetadataMatchRecord? {
        val provider = MetadataProviderId.fromId(provider) ?: return null
        return MetadataMatchRecord(titleId, provider, externalId, confidence, manual, matchedAt)
    }

    private fun MetadataMatchRecord.toEntity() =
        MetadataMatchEntity(titleId, provider.id, externalId, confidencePercent, manual, matchedAtMillis)

    companion object {
        @Volatile private var instance: RoomExternalMetadataStore? = null

        /** One per process, importing whatever the older stores left behind on first use. */
        fun get(context: Context): RoomExternalMetadataStore =
            instance ?: synchronized(this) {
                instance ?: RoomExternalMetadataStore(context).also {
                    LegacyMetadataImport(context.applicationContext, it.dao).run()
                    instance = it
                }
            }

        private const val MAX_MEDIA_ENTRIES = 2_000
        private const val MEDIA_MEMORY_ENTRIES = 300
        private const val PRUNE_EVERY_WRITES = 50
        // Well past the week a no-match and the day a failed resolution are honoured for.
        private const val FAILURE_RECORD_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1_000
    }
}

/**
 * One-time import from the two stores this one replaces - the original SharedPreferences file and
 * the short-lived plain SQLite database - so matches, manual ones above all, survive the switch.
 * Each source is deleted once imported.
 */
private class LegacyMetadataImport(private val context: Context, private val dao: MetadataDao) {
    private val matches = mutableListOf<MetadataMatchEntity>()
    private val media = mutableListOf<MetadataMediaEntity>()
    private val unresolved = mutableListOf<MetadataUnresolvedEntity>()
    private val display = mutableListOf<MetadataDisplayProviderEntity>()

    fun run() {
        val hadPreferences = runCatching { readPreferences() }
            .onFailure { AppLogger.w(TAG, "legacy preferences import failed", it) }
            .getOrDefault(false)
        val hadSqlite = runCatching { readSqlite() }
            .onFailure { AppLogger.w(TAG, "legacy SQLite import failed", it) }
            .getOrDefault(false)
        if (!hadPreferences && !hadSqlite) return
        dao.importLegacy(matches, media, unresolved, display)
        AppLogger.d(TAG, "imported matches=${matches.size} media=${media.size} unresolved=${unresolved.size} display=${display.size}")
        if (hadPreferences) context.deleteSharedPreferences(PREFS_NAME)
        if (hadSqlite) context.deleteDatabase(SQLITE_NAME)
    }

    /** Read after the preferences, so a row the SQLite store rewrote since wins. */
    private fun readSqlite(): Boolean {
        val file = context.getDatabasePath(SQLITE_NAME)
        if (!file.exists()) return false
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT title_id, provider, external_id, confidence, manual, matched_at FROM matches", null).use { c ->
                while (c.moveToNext()) {
                    matches += MetadataMatchEntity(
                        c.getString(0), c.getString(1),
                        if (c.isNull(2)) null else c.getInt(2),
                        if (c.isNull(3)) null else c.getInt(3),
                        c.getInt(4) != 0, c.getLong(5),
                    )
                }
            }
            db.rawQuery("SELECT provider, external_id, json, cached_at FROM media", null).use { c ->
                while (c.moveToNext()) media += MetadataMediaEntity(c.getString(0), c.getInt(1), c.getString(2), c.getLong(3))
            }
            db.rawQuery("SELECT source_id, provider, external_id, attempted_at FROM unresolved", null).use { c ->
                while (c.moveToNext()) unresolved += MetadataUnresolvedEntity(c.getString(0), c.getString(1), c.getInt(2), c.getLong(3))
            }
            db.rawQuery("SELECT title_id, provider FROM display_provider", null).use { c ->
                while (c.moveToNext()) display += MetadataDisplayProviderEntity(c.getString(0), c.getString(1))
            }
        }
        return true
    }

    private fun readPreferences(): Boolean {
        val entries = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all
        if (entries.isEmpty()) return false
        for ((key, value) in entries) {
            runCatching {
                when {
                    key.startsWith("match_") -> {
                        val (provider, titleId) = splitProvider(key.removePrefix("match_")) ?: return@runCatching
                        val decoded = JSONObject(value as String)
                        matches += MetadataMatchEntity(
                            titleId, provider,
                            if (decoded.isNull("externalId")) null else decoded.getInt("externalId"),
                            if (decoded.isNull("confidence")) null else decoded.getInt("confidence"),
                            decoded.optBoolean("manual", false),
                            decoded.optLong("matchedAt"),
                        )
                    }
                    key.startsWith("media_") -> {
                        val (provider, id) = splitProvider(key.removePrefix("media_")) ?: return@runCatching
                        val decoded = JSONObject(value as String)
                        media += MetadataMediaEntity(provider, id.toInt(), decoded.getString("media"), decoded.optLong("cachedAt"))
                    }
                    key.startsWith("unresolved_") -> {
                        val (provider, rest) = splitProvider(key.removePrefix("unresolved_")) ?: return@runCatching
                        val separator = rest.lastIndexOf('_')
                        unresolved += MetadataUnresolvedEntity(
                            rest.substring(0, separator), provider, rest.substring(separator + 1).toInt(), value as Long,
                        )
                    }
                    key.startsWith("display_") -> {
                        val provider = MetadataProviderId.fromId(value as String) ?: return@runCatching
                        display += MetadataDisplayProviderEntity(key.removePrefix("display_"), provider.id)
                    }
                }
            }
        }
        return true
    }

    /** "<provider>_<rest>" - provider ids never contain an underscore. */
    private fun splitProvider(text: String): Pair<String, String>? {
        val separator = text.indexOf('_')
        if (separator <= 0) return null
        val provider = MetadataProviderId.fromId(text.substring(0, separator)) ?: return null
        return provider.id to text.substring(separator + 1)
    }

    private companion object {
        const val TAG = "LegacyMetadataImport"
        const val PREFS_NAME = "hibiki_external_metadata"
        const val SQLITE_NAME = "hibiki_external_metadata.db"
    }
}
