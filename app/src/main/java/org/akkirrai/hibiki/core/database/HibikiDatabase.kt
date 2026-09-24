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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.akkirrai.hibiki.core.download.DownloadDao
import org.akkirrai.hibiki.core.download.DownloadFailedEntity
import org.akkirrai.hibiki.core.download.DownloadPendingEntity
import org.akkirrai.hibiki.core.download.DownloadPlaybackEntity
import org.akkirrai.hibiki.core.download.DownloadStoredEntity

/**
 * The app's single Room database. Stores move into it one at a time from SharedPreferences; each
 * one imports its legacy data on first use.
 *
 * A schema change must ship a Migration - never a destructive fallback: saved titles and download
 * records cannot be rebuilt from anywhere else. Schemas are exported to app/schemas for exactly that.
 */
@Database(
    entities = [
        OfflineTitleEntity::class,
        DownloadPendingEntity::class,
        DownloadStoredEntity::class,
        DownloadFailedEntity::class,
        DownloadPlaybackEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class HibikiDatabase : RoomDatabase() {
    abstract fun offlineTitleDao(): OfflineTitleDao

    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile private var instance: HibikiDatabase? = null

        /** External metadata aggregation was removed; its match and cache tables are no longer read. */
        private val DROP_METADATA_TABLES = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("metadata_matches", "metadata_media", "metadata_unresolved", "metadata_display_provider")
                    .forEach { table -> db.execSQL("DROP TABLE IF EXISTS `$table`") }
            }
        }

        fun get(context: Context): HibikiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, HibikiDatabase::class.java, "hibiki.db")
                    .addMigrations(DROP_METADATA_TABLES)
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
