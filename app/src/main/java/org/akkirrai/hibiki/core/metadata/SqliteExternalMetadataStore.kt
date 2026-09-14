package org.akkirrai.hibiki.core.metadata

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.LruCache
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.metadata.CachedMetadata
import org.akkirrai.beakokit.metadata.ExternalMetadata
import org.akkirrai.beakokit.metadata.ExternalMetadataStore
import org.akkirrai.beakokit.metadata.MetadataMatchRecord
import org.akkirrai.beakokit.metadata.MetadataProviderId
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * SQLite-backed [ExternalMetadataStore].
 *
 * Replaces a SharedPreferences store that did not scale with use: every media write rewrote the
 * whole XML file (megabytes once 500 descriptions were in it) and copied every key to check the size
 * cap, match rows were never pruned, and the reverse lookup behind opening an aggregator card
 * scanned every key. Here each write touches one row, the reverse lookup is indexed, and decoded
 * media is kept in a small memory cache, since a list screen reads the same entries on every render.
 *
 * Plain SQLiteOpenHelper rather than Room: four tables with no relations don't justify an annotation
 * processor in the build.
 */
class SqliteExternalMetadataStore private constructor(context: Context) : ExternalMetadataStore {
    private val helper = Helper(context.applicationContext)
    private val db: SQLiteDatabase by lazy { helper.writableDatabase }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mediaMemory = LruCache<String, CachedMetadata>(MEDIA_MEMORY_ENTRIES)
    private val mediaWrites = AtomicInteger()

    override fun readMatch(titleId: String, provider: MetadataProviderId): MetadataMatchRecord? =
        db.query(T_MATCHES, MATCH_COLUMNS, "title_id = ? AND provider = ?", arrayOf(titleId, provider.id), null, null, null)
            .use { if (it.moveToFirst()) it.toMatch(titleId, provider) else null }

    override fun readMatches(titleId: String): List<MetadataMatchRecord> =
        db.query(T_MATCHES, arrayOf("provider") + MATCH_COLUMNS, "title_id = ?", arrayOf(titleId), null, null, null)
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val provider = MetadataProviderId.fromId(cursor.getString(0)) ?: continue
                        add(cursor.toMatch(titleId, provider, offset = 1))
                    }
                }
            }

    override fun writeMatch(record: MetadataMatchRecord) {
        db.inTransaction {
            // A manual binding is the user's own correction; the automatic matcher must not be able
            // to walk over it, and this is the single place every write passes through.
            if (!record.manual && readMatch(record.titleId, record.provider)?.manual == true) return@inTransaction
            insertWithOnConflict(T_MATCHES, null, record.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    override fun clearMatches(titleId: String) {
        db.inTransaction {
            delete(T_MATCHES, "title_id = ?", arrayOf(titleId))
            delete(T_DISPLAY, "title_id = ?", arrayOf(titleId))
        }
    }

    override fun readDisplayProvider(titleId: String): MetadataProviderId? =
        db.query(T_DISPLAY, arrayOf("provider"), "title_id = ?", arrayOf(titleId), null, null, null)
            .use { if (it.moveToFirst()) MetadataProviderId.fromId(it.getString(0)) else null }

    override fun writeDisplayProvider(titleId: String, provider: MetadataProviderId) {
        val values = ContentValues().apply {
            put("title_id", titleId)
            put("provider", provider.id)
        }
        db.insertWithOnConflict(T_DISPLAY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun matchesForEntry(
        sourceId: String,
        provider: MetadataProviderId,
        externalId: Int,
    ): List<MetadataMatchRecord> {
        val prefix = "$sourceId:"
        return db.query(
            T_MATCHES,
            arrayOf("title_id") + MATCH_COLUMNS,
            "provider = ? AND external_id = ?",
            arrayOf(provider.id, externalId.toString()),
            null,
            null,
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val titleId = cursor.getString(0)
                    if (titleId.startsWith(prefix)) add(cursor.toMatch(titleId, provider, offset = 1))
                }
            }
        }
    }

    override fun readUnresolvedAt(sourceId: String, provider: MetadataProviderId, externalId: Int): Long? =
        db.query(
            T_UNRESOLVED,
            arrayOf("attempted_at"),
            "source_id = ? AND provider = ? AND external_id = ?",
            arrayOf(sourceId, provider.id, externalId.toString()),
            null,
            null,
            null,
        ).use { if (it.moveToFirst()) it.getLong(0) else null }

    override fun writeUnresolved(sourceId: String, provider: MetadataProviderId, externalId: Int, attemptedAtMillis: Long) {
        val values = ContentValues().apply {
            put("source_id", sourceId)
            put("provider", provider.id)
            put("external_id", externalId)
            put("attempted_at", attemptedAtMillis)
        }
        db.insertWithOnConflict(T_UNRESOLVED, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun clearUnresolved(sourceId: String, provider: MetadataProviderId, externalId: Int) {
        db.delete(
            T_UNRESOLVED,
            "source_id = ? AND provider = ? AND external_id = ?",
            arrayOf(sourceId, provider.id, externalId.toString()),
        )
    }

    override fun readMedia(provider: MetadataProviderId, externalId: Int): CachedMetadata? {
        val key = mediaMemoryKey(provider, externalId)
        mediaMemory.get(key)?.let { return it }
        val cached = db.query(
            T_MEDIA,
            arrayOf("json", "cached_at"),
            "provider = ? AND external_id = ?",
            arrayOf(provider.id, externalId.toString()),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            runCatching {
                CachedMetadata(json.decodeFromString(ExternalMetadata.serializer(), cursor.getString(0)), cursor.getLong(1))
            }.getOrNull()
        } ?: return null
        mediaMemory.put(key, cached)
        return cached
    }

    override fun writeMedia(media: ExternalMetadata, cachedAtMillis: Long) {
        val values = ContentValues().apply {
            put("provider", media.provider.id)
            put("external_id", media.externalId)
            put("json", json.encodeToString(ExternalMetadata.serializer(), media))
            put("cached_at", cachedAtMillis)
        }
        db.insertWithOnConflict(T_MEDIA, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        mediaMemory.put(mediaMemoryKey(media.provider, media.externalId), CachedMetadata(media, cachedAtMillis))
        if (mediaWrites.incrementAndGet() % PRUNE_EVERY_WRITES == 0) prune()
    }

    /** Media past the cap, oldest first, and failure records long past every TTL that reads them. */
    private fun prune() {
        val staleBefore = System.currentTimeMillis() - FAILURE_RECORD_RETENTION_MILLIS
        db.inTransaction {
            execSQL("DELETE FROM $T_MEDIA WHERE rowid IN (SELECT rowid FROM $T_MEDIA ORDER BY cached_at DESC LIMIT -1 OFFSET $MAX_MEDIA_ENTRIES)")
            delete(T_MATCHES, "external_id IS NULL AND matched_at < ?", arrayOf(staleBefore.toString()))
            delete(T_UNRESOLVED, "attempted_at < ?", arrayOf(staleBefore.toString()))
        }
    }

    private fun mediaMemoryKey(provider: MetadataProviderId, externalId: Int) = "${provider.id}_$externalId"

    private class Helper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        init {
            setWriteAheadLoggingEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $T_MATCHES (title_id TEXT NOT NULL, provider TEXT NOT NULL, external_id INTEGER, " +
                    "confidence INTEGER, manual INTEGER NOT NULL, matched_at INTEGER NOT NULL, PRIMARY KEY (title_id, provider))",
            )
            db.execSQL("CREATE INDEX idx_matches_entry ON $T_MATCHES (provider, external_id)")
            db.execSQL(
                "CREATE TABLE $T_MEDIA (provider TEXT NOT NULL, external_id INTEGER NOT NULL, json TEXT NOT NULL, " +
                    "cached_at INTEGER NOT NULL, PRIMARY KEY (provider, external_id))",
            )
            db.execSQL("CREATE INDEX idx_media_cached_at ON $T_MEDIA (cached_at)")
            db.execSQL(
                "CREATE TABLE $T_UNRESOLVED (source_id TEXT NOT NULL, provider TEXT NOT NULL, external_id INTEGER NOT NULL, " +
                    "attempted_at INTEGER NOT NULL, PRIMARY KEY (source_id, provider, external_id))",
            )
            db.execSQL("CREATE TABLE $T_DISPLAY (title_id TEXT NOT NULL PRIMARY KEY, provider TEXT NOT NULL)")
            importLegacyPreferences(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Everything here can be rebuilt from the providers except manual bindings, so a future
            // schema change must migrate rather than drop - there is no older schema yet.
        }

        /** One-time move of the old SharedPreferences store's rows, so matches (manual ones above all)
         * survive the switch. The old file is deleted afterwards. */
        private fun importLegacyPreferences(db: SQLiteDatabase) {
            val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            val entries = prefs.all
            if (entries.isEmpty()) return
            for ((key, value) in entries) {
                runCatching {
                    when {
                        key.startsWith(LEGACY_MATCH) -> {
                            val (provider, titleId) = splitProvider(key.removePrefix(LEGACY_MATCH)) ?: return@runCatching
                            val decoded = JSONObject(value as String)
                            val record = MetadataMatchRecord(
                                titleId = titleId,
                                provider = provider,
                                externalId = if (decoded.isNull("externalId")) null else decoded.getInt("externalId"),
                                confidencePercent = if (decoded.isNull("confidence")) null else decoded.getInt("confidence"),
                                manual = decoded.optBoolean("manual", false),
                                matchedAtMillis = decoded.optLong("matchedAt"),
                            )
                            db.insertWithOnConflict(T_MATCHES, null, record.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
                        }
                        key.startsWith(LEGACY_MEDIA) -> {
                            val (provider, id) = splitProvider(key.removePrefix(LEGACY_MEDIA)) ?: return@runCatching
                            val decoded = JSONObject(value as String)
                            val values = ContentValues().apply {
                                put("provider", provider.id)
                                put("external_id", id.toInt())
                                put("json", decoded.getString("media"))
                                put("cached_at", decoded.optLong("cachedAt"))
                            }
                            db.insertWithOnConflict(T_MEDIA, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                        }
                        key.startsWith(LEGACY_UNRESOLVED) -> {
                            val (provider, rest) = splitProvider(key.removePrefix(LEGACY_UNRESOLVED)) ?: return@runCatching
                            val separator = rest.lastIndexOf('_')
                            val values = ContentValues().apply {
                                put("source_id", rest.substring(0, separator))
                                put("provider", provider.id)
                                put("external_id", rest.substring(separator + 1).toInt())
                                put("attempted_at", value as Long)
                            }
                            db.insertWithOnConflict(T_UNRESOLVED, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                        }
                        key.startsWith(LEGACY_DISPLAY) -> {
                            val provider = MetadataProviderId.fromId(value as String) ?: return@runCatching
                            val values = ContentValues().apply {
                                put("title_id", key.removePrefix(LEGACY_DISPLAY))
                                put("provider", provider.id)
                            }
                            db.insertWithOnConflict(T_DISPLAY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                        }
                    }
                }
            }
            context.deleteSharedPreferences(LEGACY_PREFS_NAME)
        }

        /** "<provider>_<rest>" - provider ids never contain an underscore. */
        private fun splitProvider(text: String): Pair<MetadataProviderId, String>? {
            val separator = text.indexOf('_')
            if (separator <= 0) return null
            val provider = MetadataProviderId.fromId(text.substring(0, separator)) ?: return null
            return provider to text.substring(separator + 1)
        }
    }

    companion object {
        @Volatile private var instance: SqliteExternalMetadataStore? = null

        /** One per process: every repository shares the same database and memory cache. */
        fun get(context: Context): SqliteExternalMetadataStore =
            instance ?: synchronized(this) {
                instance ?: SqliteExternalMetadataStore(context).also { instance = it }
            }

        private const val DB_NAME = "hibiki_external_metadata.db"
        private const val DB_VERSION = 1
        private const val T_MATCHES = "matches"
        private const val T_MEDIA = "media"
        private const val T_UNRESOLVED = "unresolved"
        private const val T_DISPLAY = "display_provider"
        private val MATCH_COLUMNS = arrayOf("external_id", "confidence", "manual", "matched_at")

        private const val MAX_MEDIA_ENTRIES = 2_000
        private const val MEDIA_MEMORY_ENTRIES = 300
        private const val PRUNE_EVERY_WRITES = 50
        // Well past the week a no-match and the day a failed resolution are honoured for.
        private const val FAILURE_RECORD_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1_000

        private const val LEGACY_PREFS_NAME = "hibiki_external_metadata"
        private const val LEGACY_MATCH = "match_"
        private const val LEGACY_MEDIA = "media_"
        private const val LEGACY_UNRESOLVED = "unresolved_"
        private const val LEGACY_DISPLAY = "display_"

        private fun Cursor.toMatch(titleId: String, provider: MetadataProviderId, offset: Int = 0) = MetadataMatchRecord(
            titleId = titleId,
            provider = provider,
            externalId = if (isNull(offset)) null else getInt(offset),
            confidencePercent = if (isNull(offset + 1)) null else getInt(offset + 1),
            manual = getInt(offset + 2) != 0,
            matchedAtMillis = getLong(offset + 3),
        )

        private fun MetadataMatchRecord.toValues() = ContentValues().apply {
            put("title_id", titleId)
            put("provider", provider.id)
            if (externalId == null) putNull("external_id") else put("external_id", externalId)
            if (confidencePercent == null) putNull("confidence") else put("confidence", confidencePercent)
            put("manual", if (manual) 1 else 0)
            put("matched_at", matchedAtMillis)
        }

        private inline fun SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> Unit) {
            beginTransaction()
            try {
                block()
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }
}
