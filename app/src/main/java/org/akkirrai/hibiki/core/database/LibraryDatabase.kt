package org.akkirrai.hibiki.core.database

import android.content.Context
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

@Entity(tableName = "library_entries")
data class LibraryEntryEntity(
    /** Normalized title id - see YummyIdMigration. */
    @PrimaryKey @ColumnInfo(name = "title_id") val titleId: String,
    /** The title as it was saved, so the library renders offline. Null only for rows imported from a
     * legacy record that had categories but no payload. */
    @ColumnInfo(name = "anime_json") val animeJson: String?,
    /** LibraryCategory storage values, comma-separated. */
    val categories: String,
    @ColumnInfo(name = "added_at") val addedAt: Long?,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_entries ORDER BY added_at DESC")
    fun all(): List<LibraryEntryEntity>

    @Query("SELECT * FROM library_entries WHERE title_id = :titleId")
    fun entry(titleId: String): LibraryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entry: LibraryEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entries: List<LibraryEntryEntity>)

    @Query("DELETE FROM library_entries WHERE title_id = :titleId")
    fun delete(titleId: String)
}

/**
 * The library, in a database of its own rather than [HibikiDatabase], because it is user data that
 * Android backup must carry (see res/xml/backup_rules.xml) while the other database is a cache.
 *
 * TRUNCATE journal instead of WAL: backup copies the database file alone, and with WAL the newest
 * writes can still be sitting in the -wal file beside it.
 */
@Database(entities = [LibraryEntryEntity::class], version = 1, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val NAME = "hibiki_library.db"

        @Volatile private var instance: LibraryDatabase? = null

        fun get(context: Context): LibraryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, NAME)
                    .setJournalMode(JournalMode.TRUNCATE)
                    // Library reads are single-table and small, and several screens (a card's status
                    // badge, the details header) make them synchronously while composing.
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
    }
}
