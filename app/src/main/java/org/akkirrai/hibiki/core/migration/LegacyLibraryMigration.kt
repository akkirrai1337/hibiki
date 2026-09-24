package org.akkirrai.hibiki.core.migration

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.core.database.LibraryDatabase
import org.akkirrai.hibiki.core.database.LibraryEntryEntity
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** A retired source that library entries still point at. */
data class LegacySource(val id: String, val name: String, val entryCount: Int)

data class LegacyMigrationResult(val migrated: Int, val total: Int, val unmatchedTitles: List<String>)

/**
 * Moves the library from the retired scripted sources to their APK counterparts.
 *
 * Title ids differ between the two implementations of a source, so an entry cannot simply be
 * renamed: each saved title is searched for in the new source and replaced only when the match is
 * confident. Everything else stays exactly as it was. Each entry is rewritten in its own
 * transaction, so an interrupted run leaves a consistent library and can be started again.
 */
class LegacyLibraryMigration(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val database get() = LibraryDatabase.get(appContext)

    /** Set once the user asks not to be offered the transfer again. */
    var dismissedForever: Boolean
        get() = prefs.getBoolean(KEY_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_DISMISSED, value).apply()

    /** The retired sources with entries still in the library, most entries first. */
    suspend fun scan(): List<LegacySource> = withContext(Dispatchers.IO) {
        // Opening the repository runs the one-time import of the very old preferences-based library.
        LibraryRepository(appContext)
        database.libraryDao().all()
            .mapNotNull { LegacyLibraryMatching.legacySourceIdOf(it.titleId) }
            .groupingBy { it }
            .eachCount()
            .map { (id, count) -> LegacySource(id, LEGACY_JS_SOURCES.getValue(id), count) }
            .sortedByDescending(LegacySource::entryCount)
    }

    /**
     * Transfers the entries of every source in [targets] (legacy source id -> installed source).
     * [onProgress] gets (done, total) as entries are processed.
     */
    suspend fun migrate(
        targets: Map<String, SourceId>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): LegacyMigrationResult = withContext(Dispatchers.IO) {
        val dao = database.libraryDao()
        val work = dao.all().mapNotNull { entry ->
            val legacyId = LegacyLibraryMatching.legacySourceIdOf(entry.titleId) ?: return@mapNotNull null
            targets[legacyId]?.let { target -> Triple(entry, legacyId, target) }
        }
        val runtimes = targets.values.distinct().associateWith { AnimeSourceRegistry.createRuntime(appContext, it) }
        val done = AtomicInteger(0)
        val migrated = AtomicInteger(0)
        val unmatched = java.util.Collections.synchronizedList(mutableListOf<String>())
        val slots = Semaphore(PARALLEL_SEARCHES)
        onProgress(0, work.size)
        coroutineScope {
            work.map { (entry, _, target) ->
                async {
                    val title = slots.withPermit { transferEntry(entry, runtimes.getValue(target)) }
                    if (title == null) unmatched += entryTitle(entry) else migrated.incrementAndGet()
                    onProgress(done.incrementAndGet(), work.size)
                }
            }.awaitAll()
        }
        LegacyMigrationResult(migrated.get(), work.size, unmatched.sorted())
    }

    /** Replaces one entry with the same title from [runtime]; returns the new title, or null when it was not found. */
    private suspend fun transferEntry(
        entry: LibraryEntryEntity,
        runtime: org.akkirrai.hibiki.core.source.AnimeSourceRuntime,
    ): String? {
        val json = entry.animeJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val probe = LegacyLibraryMatching.probeFor(json)
        val match = LegacyLibraryMatching.queriesFor(probe).firstNotNullOfOrNull { query ->
            val candidates = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                runCatching { runtime.search(query) }
                    .onFailure { AppLogger.w(TAG, "search failed for a saved title: ${it.message}") }
                    .getOrNull()
            }.orEmpty()
            LegacyLibraryMatching.bestMatch(probe, candidates)
        } ?: return null

        val rewritten = LegacyLibraryMatching.rewritten(json, match).toString()
        database.runInTransaction {
            val dao = database.libraryDao()
            val existing = dao.entry(match.id)
            val categories = (entry.categories.split(',') + existing?.categories.orEmpty().split(','))
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(",")
            dao.upsert(
                LibraryEntryEntity(
                    titleId = match.id,
                    animeJson = rewritten,
                    categories = categories,
                    addedAt = listOfNotNull(entry.addedAt, existing?.addedAt).minOrNull(),
                ),
            )
            dao.delete(entry.titleId)
        }
        return match.id
    }

    private fun entryTitle(entry: LibraryEntryEntity): String =
        entry.animeJson?.let { runCatching { JSONObject(it).optString("title") }.getOrNull() }
            ?.takeIf(String::isNotBlank)
            ?: entry.titleId

    private companion object {
        const val TAG = "LegacyLibraryMigration"
        const val PREFS_NAME = "legacy_library_migration"
        const val KEY_DISMISSED = "dismissed_forever"
        const val PARALLEL_SEARCHES = 3
        const val SEARCH_TIMEOUT_MS = 20_000L
    }
}
