package org.akkirrai.hibiki.core.metadata

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Entity(
    tableName = "metadata_matches",
    primaryKeys = ["title_id", "provider"],
    // The match table read backwards - which titles point at one provider entry.
    indices = [Index(value = ["provider", "external_id"])],
)
data class MetadataMatchEntity(
    @ColumnInfo(name = "title_id") val titleId: String,
    val provider: String,
    /** Null records a failed search. */
    @ColumnInfo(name = "external_id") val externalId: Int?,
    val confidence: Int?,
    val manual: Boolean,
    @ColumnInfo(name = "matched_at") val matchedAt: Long,
)

@Entity(
    tableName = "metadata_media",
    primaryKeys = ["provider", "external_id"],
    indices = [Index(value = ["cached_at"])],
)
data class MetadataMediaEntity(
    val provider: String,
    @ColumnInfo(name = "external_id") val externalId: Int,
    val json: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
)

@Entity(tableName = "metadata_unresolved", primaryKeys = ["source_id", "provider", "external_id"])
data class MetadataUnresolvedEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    val provider: String,
    @ColumnInfo(name = "external_id") val externalId: Int,
    @ColumnInfo(name = "attempted_at") val attemptedAt: Long,
)

@Entity(tableName = "metadata_display_provider")
data class MetadataDisplayProviderEntity(
    @PrimaryKey @ColumnInfo(name = "title_id") val titleId: String,
    val provider: String,
)

@Dao
abstract class MetadataDao {
    @Query("SELECT * FROM metadata_matches WHERE title_id = :titleId AND provider = :provider")
    abstract fun match(titleId: String, provider: String): MetadataMatchEntity?

    @Query("SELECT * FROM metadata_matches WHERE title_id = :titleId")
    abstract fun matches(titleId: String): List<MetadataMatchEntity>

    @Query("SELECT * FROM metadata_matches WHERE title_id IN (:titleIds)")
    abstract fun matches(titleIds: List<String>): List<MetadataMatchEntity>

    @Query("SELECT * FROM metadata_matches WHERE provider = :provider AND external_id = :externalId")
    abstract fun matchesForEntry(provider: String, externalId: Int): List<MetadataMatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertMatch(match: MetadataMatchEntity)

    /** A manual binding is the user's own correction; an automatic one never replaces it. */
    @Transaction
    open fun writeMatch(match: MetadataMatchEntity) {
        if (!match.manual && match(match.titleId, match.provider)?.manual == true) return
        upsertMatch(match)
    }

    @Query("DELETE FROM metadata_matches WHERE title_id = :titleId")
    abstract fun deleteMatches(titleId: String)

    @Query("DELETE FROM metadata_display_provider WHERE title_id = :titleId")
    abstract fun deleteDisplayProvider(titleId: String)

    @Transaction
    open fun clearTitle(titleId: String) {
        deleteMatches(titleId)
        deleteDisplayProvider(titleId)
    }

    @Query("SELECT provider FROM metadata_display_provider WHERE title_id = :titleId")
    abstract fun displayProvider(titleId: String): String?

    @Query("SELECT * FROM metadata_display_provider WHERE title_id IN (:titleIds)")
    abstract fun displayProviders(titleIds: List<String>): List<MetadataDisplayProviderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertDisplayProvider(display: MetadataDisplayProviderEntity)

    @Query("SELECT attempted_at FROM metadata_unresolved WHERE source_id = :sourceId AND provider = :provider AND external_id = :externalId")
    abstract fun unresolvedAt(sourceId: String, provider: String, externalId: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertUnresolved(unresolved: MetadataUnresolvedEntity)

    @Query("DELETE FROM metadata_unresolved WHERE source_id = :sourceId AND provider = :provider AND external_id = :externalId")
    abstract fun deleteUnresolved(sourceId: String, provider: String, externalId: Int)

    @Query("SELECT * FROM metadata_media WHERE provider = :provider AND external_id = :externalId")
    abstract fun media(provider: String, externalId: Int): MetadataMediaEntity?

    @Query("SELECT * FROM metadata_media WHERE provider = :provider AND external_id IN (:externalIds)")
    abstract fun media(provider: String, externalIds: List<Int>): List<MetadataMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertMedia(media: MetadataMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertMedia(media: List<MetadataMediaEntity>)

    /** Media past the cap, oldest first, and failure records long past every TTL that reads them. */
    @Transaction
    open fun prune(keepMedia: Int, failuresBefore: Long) {
        pruneMedia(keepMedia)
        pruneNoMatches(failuresBefore)
        pruneUnresolved(failuresBefore)
    }

    @Query("DELETE FROM metadata_media WHERE rowid IN (SELECT rowid FROM metadata_media ORDER BY cached_at DESC LIMIT -1 OFFSET :keep)")
    protected abstract fun pruneMedia(keep: Int)

    @Query("DELETE FROM metadata_matches WHERE external_id IS NULL AND matched_at < :before")
    protected abstract fun pruneNoMatches(before: Long)

    @Query("DELETE FROM metadata_unresolved WHERE attempted_at < :before")
    protected abstract fun pruneUnresolved(before: Long)

    /** Bulk insert for a one-time legacy import. */
    @Transaction
    open fun importLegacy(
        matches: List<MetadataMatchEntity>,
        media: List<MetadataMediaEntity>,
        unresolved: List<MetadataUnresolvedEntity>,
        display: List<MetadataDisplayProviderEntity>,
    ) {
        insertMatches(matches)
        insertMedia(media)
        insertUnresolved(unresolved)
        insertDisplayProviders(display)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertMatches(matches: List<MetadataMatchEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertMedia(media: List<MetadataMediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertUnresolved(unresolved: List<MetadataUnresolvedEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertDisplayProviders(display: List<MetadataDisplayProviderEntity>)
}
