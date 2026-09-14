package org.akkirrai.hibiki.core.download

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/** An episode waiting to be resolved and handed to Media3, in request order. */
@Entity(tableName = "download_pending")
data class DownloadPendingEntity(
    @PrimaryKey(autoGenerate = true) val position: Long = 0,
    val json: String,
)

/** Every episode the user asked to download and has not removed, in request order. */
@Entity(tableName = "download_stored")
data class DownloadStoredEntity(
    @PrimaryKey(autoGenerate = true) val position: Long = 0,
    val json: String,
)

@Entity(tableName = "download_failed")
data class DownloadFailedEntity(
    @PrimaryKey @ColumnInfo(name = "download_id") val downloadId: String,
)

/** The playback snapshot an episode was downloaded with. */
@Entity(tableName = "download_playback")
data class DownloadPlaybackEntity(
    @PrimaryKey @ColumnInfo(name = "download_id") val downloadId: String,
    val json: String,
)

/**
 * Storage behind [OfflineDownloadQueue]. The queue still reads and writes whole lists under its own
 * lock, but each write is now one transaction instead of a JSON array rewritten through
 * SharedPreferences' asynchronous apply(), where a process death between two list writes could
 * leave the pending and stored lists disagreeing.
 */
@Dao
abstract class DownloadDao {
    @Query("SELECT json FROM download_pending ORDER BY position")
    abstract fun pending(): List<String>

    @Transaction
    open fun replacePending(json: List<String>) {
        clearPending()
        insertPending(json.map { DownloadPendingEntity(json = it) })
    }

    @Query("SELECT json FROM download_stored ORDER BY position")
    abstract fun stored(): List<String>

    @Transaction
    open fun replaceStored(json: List<String>) {
        clearStored()
        insertStored(json.map { DownloadStoredEntity(json = it) })
    }

    @Query("SELECT download_id FROM download_failed")
    abstract fun failedIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun addFailed(failed: DownloadFailedEntity)

    @Query("DELETE FROM download_failed WHERE download_id IN (:downloadIds)")
    abstract fun removeFailed(downloadIds: List<String>)

    @Query("SELECT json FROM download_playback WHERE download_id = :downloadId")
    abstract fun playback(downloadId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertPlayback(playback: DownloadPlaybackEntity)

    @Query("DELETE FROM download_playback WHERE download_id = :downloadId")
    abstract fun deletePlayback(downloadId: String)

    /** One-time import of the SharedPreferences queue. */
    @Transaction
    open fun importLegacy(
        pending: List<String>,
        stored: List<String>,
        failed: Set<String>,
        playback: Map<String, String>,
    ) {
        replacePending(pending)
        replaceStored(stored)
        failed.forEach { addFailed(DownloadFailedEntity(it)) }
        playback.forEach { (downloadId, json) -> upsertPlayback(DownloadPlaybackEntity(downloadId, json)) }
    }

    @Query("DELETE FROM download_pending")
    protected abstract fun clearPending()

    @Insert
    protected abstract fun insertPending(rows: List<DownloadPendingEntity>)

    @Query("DELETE FROM download_stored")
    protected abstract fun clearStored()

    @Insert
    protected abstract fun insertStored(rows: List<DownloadStoredEntity>)
}
