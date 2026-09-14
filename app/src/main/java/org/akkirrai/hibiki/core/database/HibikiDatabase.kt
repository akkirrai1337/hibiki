package org.akkirrai.hibiki.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class HibikiDatabase : RoomDatabase() {
    abstract fun metadataDao(): MetadataDao

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
