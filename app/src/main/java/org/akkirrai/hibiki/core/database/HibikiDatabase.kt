package org.akkirrai.hibiki.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import org.akkirrai.hibiki.core.download.DownloadDao
import org.akkirrai.hibiki.core.download.DownloadFailedEntity
import org.akkirrai.hibiki.core.download.DownloadPendingEntity
import org.akkirrai.hibiki.core.download.DownloadPlaybackEntity
import org.akkirrai.hibiki.core.download.DownloadStoredEntity
import org.akkirrai.hibiki.core.metadata.MetadataDao
import org.akkirrai.hibiki.core.metadata.MetadataDisplayProviderEntity
import org.akkirrai.hibiki.core.metadata.MetadataMatchEntity
import org.akkirrai.hibiki.core.metadata.MetadataMediaEntity
import org.akkirrai.hibiki.core.metadata.MetadataUnresolvedEntity

/**
 * The app's single Room database. Stores move into it one at a time from SharedPreferences; each
 * one imports its legacy data on first use.
 *
 * A schema change must ship a Migration - never a destructive fallback: manual metadata bindings
 * (and, later, the library) cannot be rebuilt from anywhere else. Schemas are exported to
 * app/schemas for exactly that.
 */
@Database(
    entities = [
        MetadataMatchEntity::class,
        MetadataMediaEntity::class,
        MetadataUnresolvedEntity::class,
        MetadataDisplayProviderEntity::class,
        OfflineTitleEntity::class,
        DownloadPendingEntity::class,
        DownloadStoredEntity::class,
        DownloadFailedEntity::class,
        DownloadPlaybackEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class HibikiDatabase : RoomDatabase() {
    abstract fun metadataDao(): MetadataDao

    abstract fun offlineTitleDao(): OfflineTitleDao

    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile private var instance: HibikiDatabase? = null

        fun get(context: Context): HibikiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, HibikiDatabase::class.java, "hibiki.db")
                    // The stores behind it implement synchronous interfaces that a few screens still
                    // read from the main thread; each such read is a single indexed row.
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
    }
}

@Entity(tableName = "offline_titles")
data class OfflineTitleEntity(
    @PrimaryKey @ColumnInfo(name = "title_id") val titleId: String,
    val json: String,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
)

@Dao
interface OfflineTitleDao {
    @Query("SELECT * FROM offline_titles WHERE title_id IN (:titleIds)")
    fun find(titleIds: List<String>): List<OfflineTitleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(title: OfflineTitleEntity)

    /** A legacy import never overwrites a title saved since. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIfAbsent(titles: List<OfflineTitleEntity>)
}
