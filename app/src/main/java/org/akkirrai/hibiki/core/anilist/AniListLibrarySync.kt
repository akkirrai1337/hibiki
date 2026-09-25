package org.akkirrai.hibiki.core.anilist

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.matching.TitleMatcher
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.extension.RepositoryCatalogCache

class AniListNsfwSourceException : IllegalStateException("The chosen source is marked 18+")

data class AniListSyncReport(
    val added: Int,
    val updated: Int,
    val unmatched: List<String>,
    val failed: Int,
    val finishedAtMillis: Long,
)

/**
 * One-way library sync, AniList to Hibiki.
 *
 * AniList knows nothing about sources, so every title first has to be found on the chosen source. That
 * lookup is the fragile part, and the rest is built around not trusting it blindly:
 * - a title is only linked when its best candidate clearly wins (names, year, episode count and season
 *   number all agree), otherwise it is reported as unmatched and nothing is written for it;
 * - what was decided is remembered per source, so a title is searched once, and titles that were not
 *   found are only retried after a while;
 * - AniList's status is applied only when it *changed* since the last sync, so a category the user
 *   moved by hand stays until the AniList side moves again;
 * - nothing is ever removed from the local library.
 */
class AniListLibrarySync(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val library = LibraryRepository(appContext)

    var userName: String
        get() = prefs.getString(KEY_USER, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_USER, value.trim()).apply() }

    /** Read the list of the signed-in account (true) or a public list by [userName] (false). */
    var useAccount: Boolean
        get() = prefs.getBoolean(KEY_USE_ACCOUNT, true)
        set(value) { prefs.edit().putBoolean(KEY_USE_ACCOUNT, value).apply() }

    /** Whether the sync runs by itself when the app starts (see [AniListAutoSync]). */
    var autoEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO, false)
        set(value) { prefs.edit().putBoolean(KEY_AUTO, value).apply() }

    /** When a sync last finished, by hand or automatically; zero if none has. */
    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        private set(value) { prefs.edit().putLong(KEY_LAST_SYNC, value).apply() }

    /** Whether the library holds a title that was not in it when the last sync finished. */
    fun hasNewTitles(): Boolean {
        val known = prefs.getStringSet(KEY_KNOWN_TITLES, emptySet()).orEmpty()
        return libraryTitleIds().any { it !in known }
    }

    /** Called when a sync finishes: restarts the wait for the next automatic one and records what the library holds. */
    internal fun markSynced() {
        prefs.edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .putStringSet(KEY_KNOWN_TITLES, libraryTitleIds())
            .apply()
    }

    // Saved only mirrors downloads, so a download alone is not a new title worth syncing.
    private fun libraryTitleIds(): Set<String> = library.getLibraryEntries()
        .filter { it.category != LibraryCategory.Saved }
        .mapTo(mutableSetOf()) { it.anime.id }

    /** Leave out titles from sources marked 18+: they are neither imported nor sent to AniList. */
    var skipNsfw: Boolean
        get() = prefs.getBoolean(KEY_SKIP_NSFW, true)
        set(value) { prefs.edit().putBoolean(KEY_SKIP_NSFW, value).apply() }

    var sourceId: String
        get() = prefs.getString(KEY_SOURCE, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_SOURCE, value).apply() }

    fun lastReport(): AniListSyncReport? = prefs.getString(KEY_REPORT, null)
        ?.let { runCatching { json.decodeFromString<StoredReport>(it) }.getOrNull() }
        ?.let { AniListSyncReport(it.added, it.updated, it.unmatched, it.failed, it.finishedAt) }

    suspend fun run(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): AniListSyncReport {
        val source = sourceId.ifBlank { error("Sync source is not chosen") }
        if (skipNsfw && isNsfwSource(source)) throw AniListNsfwSourceException()
        val client = AndroidHttpClientFactory.create()
        val search = AnimeSearchRepository(appContext, client = client, closeClientOnClose = false)
        try {
            // Signed in, the account decides whose list this is and private lists are readable.
            val token = AniListRepository(appContext, client).currentAccessToken()?.takeIf { useAccount }
            val user = if (token != null) {
                AniListRepository(appContext, client).getViewer().name
            } else {
                userName.ifBlank { error("AniList user name is not set") }
            }
            val remote = AniListPublicLibrary(client, token)
            val entries = remote.library(user)
            val favourites = remote.favourites(user)
            val favouriteIds = favourites.mapTo(mutableSetOf()) { it.mediaId }
            // A favourite that is on no list still needs a card; it carries no status.
            val all = entries + favourites.filter { fav -> entries.none { it.mediaId == fav.mediaId } }

            val state = loadState()
            val pending = all.filter { entry ->
                val key = "$source:${entry.mediaId}"
                val category = entry.status?.toCategory()
                (category != null && state.applied[key] != category.storageValue) ||
                    (entry.mediaId in favouriteIds && key !in state.favouritesApplied)
            }

            var added = 0
            var updated = 0
            var failed = 0
            val unmatched = mutableListOf<String>()
            var done = 0
            val gate = Semaphore(LOOKUP_CONCURRENCY)
            onProgress(0, pending.size)

            coroutineScope {
                pending.map { entry ->
                    async {
                        val outcome = gate.withPermit { resolve(search, SourceId(source), entry, state, source) }
                        synchronized(this@AniListLibrarySync) {
                            done++
                            onProgress(done, pending.size)
                        }
                        entry to outcome
                    }
                }.map { it.await() }
            }.forEach { (entry, outcome) ->
                val key = "$source:${entry.mediaId}"
                when (outcome) {
                    is Outcome.Failed -> failed++
                    Outcome.NotFound -> unmatched += entry.names.firstOrNull() ?: entry.mediaId.toString()
                    is Outcome.Found -> {
                        val category = entry.status?.toCategory()
                        val existedBefore = library.getLibraryCategories(outcome.anime.id).isNotEmpty()
                        if (category != null && state.applied[key] != category.storageValue) {
                            val before = library.getLibraryCategories(outcome.anime.id)
                            library.saveToLibrary(outcome.anime, category)
                            state.applied[key] = category.storageValue
                            if (!existedBefore) added++ else if (category !in before) updated++
                        }
                        if (entry.mediaId in favouriteIds && key !in state.favouritesApplied) {
                            library.saveToLibrary(outcome.anime, LibraryCategory.Favorite)
                            state.favouritesApplied += key
                            if (!existedBefore && category == null) added++
                        }
                    }
                }
            }
            saveState(state)
            markSynced()
            return AniListSyncReport(added, updated, unmatched, failed, System.currentTimeMillis()).also { report ->
                prefs.edit().putString(
                    KEY_REPORT,
                    json.encodeToString(
                        StoredReport(report.added, report.updated, report.unmatched, report.failed, report.finishedAtMillis),
                    ),
                ).apply()
            }
        } finally {
            search.close()
            client.close()
        }
    }

    /** Every title a sync has linked, by its title id, for any source. */
    internal fun linkedMediaIds(): Map<String, Int> = loadState().links
        .filterValues { it.titleId.isNotBlank() }
        .entries.associate { (key, link) -> link.titleId to key.substringAfterLast(':').toInt() }

    /** The category the last import applied for a title, whichever source it went through. */
    internal fun appliedCategory(mediaId: Int): String? = loadState().applied.entries
        .firstOrNull { it.key.endsWith(":$mediaId") }?.value

    /** The best AniList candidate for a title of the app, or null unless it clearly wins. */
    internal fun pickBestAniList(local: AnimeTitle, candidates: List<AniListSyncEntry>): Int? {
        val scored = candidates.map { it to confidence(it, local) }.sortedByDescending { it.second }
        val (best, bestScore) = scored.firstOrNull() ?: return null
        if (bestScore < MIN_CONFIDENCE) return null
        val runnerUp = scored.getOrNull(1)?.second ?: 0.0
        if (bestScore - runnerUp < MIN_MARGIN) return null
        return best.mediaId
    }

    internal fun isNsfwTitle(titleId: String): Boolean =
        AnimeKey.parse(titleId)?.sourceId?.value?.let(::isNsfwSource) == true

    /** Whether the extension behind a source is marked 18+ in any of the repositories that list it. */
    private fun isNsfwSource(sourceId: String): Boolean {
        val packageName = AnimeSourceRegistry.apkPackageForSource(SourceId(sourceId)) ?: return false
        return AppPreferences.readSourceRepositoryUrls(appContext).any { url ->
            RepositoryCatalogCache.get(appContext, url)?.extensions
                ?.any { it.pkg == packageName && it.nsfw != 0 } == true
        }
    }

    private sealed interface Outcome {
        data class Found(val anime: Anime) : Outcome
        data object NotFound : Outcome
        data class Failed(val error: Throwable) : Outcome
    }

    private suspend fun resolve(
        search: AnimeSearchRepository,
        source: SourceId,
        entry: AniListSyncEntry,
        state: SyncState,
        sourceKey: String,
    ): Outcome {
        val key = "$sourceKey:${entry.mediaId}"
        state.links[key]?.let { link ->
            if (link.titleId.isNotBlank()) {
                // The card is stored with the title it was linked to; look it up again for its data.
                return findByLink(search, source, entry, link.titleId) ?: Outcome.NotFound
            }
            if (System.currentTimeMillis() - link.checkedAt < RETRY_UNMATCHED_AFTER_MILLIS) return Outcome.NotFound
        }
        return try {
            val candidates = LinkedHashMap<String, Pair<AnimeTitle, Anime>>()
            // Two queries at most: the English then the romaji name. Sources differ in which one they know.
            entry.names.take(2).forEach { name ->
                search.findOnSource(source, name).forEach { candidates.putIfAbsent(it.first.id, it) }
            }
            val best = pickBest(entry, candidates.values.toList())
            if (best == null) {
                state.links[key] = Link(titleId = "", checkedAt = System.currentTimeMillis())
                Outcome.NotFound
            } else {
                state.links[key] = Link(titleId = best.first.id, checkedAt = System.currentTimeMillis())
                Outcome.Found(best.second)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: NoInternetConnectionException) {
            throw error
        } catch (error: Throwable) {
            AppLogger.w(TAG, "Lookup failed for AniList media ${entry.mediaId}", error)
            Outcome.Failed(error)
        }
    }

    private suspend fun findByLink(
        search: AnimeSearchRepository,
        source: SourceId,
        entry: AniListSyncEntry,
        titleId: String,
    ): Outcome? = try {
        val name = entry.names.firstOrNull() ?: return null
        search.findOnSource(source, name).firstOrNull { it.first.id == titleId }
            ?.let { Outcome.Found(it.second) }
            // The source no longer lists it under that name; the stored card is enough to keep the link.
            ?: library.getLibraryEntries().firstOrNull { it.anime.id == titleId }?.let { Outcome.Found(it.anime) }
    } catch (error: CancellationException) {
        throw error
    } catch (error: NoInternetConnectionException) {
        throw error
    } catch (error: Throwable) {
        Outcome.Failed(error)
    }

    private fun pickBest(entry: AniListSyncEntry, candidates: List<Pair<AnimeTitle, Anime>>): Pair<AnimeTitle, Anime>? {
        val scored = candidates
            .map { it to confidence(entry, it.first) }
            .sortedByDescending { it.second }
        val (best, bestScore) = scored.firstOrNull() ?: return null
        if (bestScore < MIN_CONFIDENCE) return null
        val runnerUp = scored.getOrNull(1)?.second ?: 0.0
        // Two near-equal candidates means it is a guess; a wrong link would put the wrong show in the library.
        if (bestScore - runnerUp < MIN_MARGIN) return null
        return best
    }

    /**
     * Name similarity, compared only between names that mention the same season number: the matcher
     * on its own drops "Season 2" from names, which would rate a sequel as a perfect match for the
     * first season. A year further than one apart rules a candidate out.
     */
    private fun confidence(entry: AniListSyncEntry, title: AnimeTitle): Double {
        val remoteYear = entry.year
        val localYear = title.year
        if (remoteYear != null && localYear != null && kotlin.math.abs(remoteYear - localYear) > 1) return 0.0
        val remoteByMarker = entry.names.groupBy(::seasonMarker)
        val localByMarker = title.allNames().groupBy(::seasonMarker)
        val candidateType = if (entry.format.equals("MOVIE", ignoreCase = true)) "movie" else null
        return remoteByMarker.keys.intersect(localByMarker.keys).maxOfOrNull { marker ->
            val localNames = localByMarker.getValue(marker)
            matcher.confidence(
                title = title.copy(
                    originalName = localNames.first(),
                    russianName = null,
                    englishName = null,
                    japaneseName = null,
                    synonyms = localNames.drop(1),
                ),
                candidateNames = remoteByMarker.getValue(marker),
                candidateYear = entry.year,
                candidateType = candidateType,
                candidateEpisodes = entry.episodes,
            )
        } ?: 0.0
    }

    private fun seasonMarker(name: String): Int {
        val text = name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        SEASON_PATTERNS.firstNotNullOfOrNull { it.find(text)?.groupValues?.drop(1)?.firstOrNull(String::isNotEmpty) }
            ?.toIntOrNull()?.let { return it }
        ROMAN.entries.firstOrNull { (numeral, _) -> text.endsWith(" $numeral") }?.let { return it.value }
        return 1
    }

    private fun AniListMediaListStatus.toCategory(): LibraryCategory = when (this) {
        AniListMediaListStatus.CURRENT, AniListMediaListStatus.REPEATING -> LibraryCategory.Watching
        AniListMediaListStatus.PLANNING -> LibraryCategory.Planned
        AniListMediaListStatus.COMPLETED -> LibraryCategory.Completed
        AniListMediaListStatus.DROPPED -> LibraryCategory.Dropped
        AniListMediaListStatus.PAUSED -> LibraryCategory.OnHold
    }

    private class SyncState(
        val links: MutableMap<String, Link>,
        val applied: MutableMap<String, String>,
        val favouritesApplied: MutableSet<String>,
    )

    private fun loadState(): SyncState {
        val stored = prefs.getString(KEY_STATE, null)
            ?.let { runCatching { json.decodeFromString<StoredState>(it) }.getOrNull() }
            ?: StoredState()
        return SyncState(
            java.util.concurrent.ConcurrentHashMap(stored.links),
            java.util.concurrent.ConcurrentHashMap(stored.applied),
            java.util.concurrent.ConcurrentHashMap.newKeySet<String>().also { it.addAll(stored.favourites) },
        )
    }

    private fun saveState(state: SyncState) {
        prefs.edit().putString(
            KEY_STATE,
            json.encodeToString(StoredState(state.links, state.applied, state.favouritesApplied)),
        ).apply()
    }

    @Serializable
    private data class Link(val titleId: String, val checkedAt: Long)

    @Serializable
    private data class StoredState(
        val links: Map<String, Link> = emptyMap(),
        val applied: Map<String, String> = emptyMap(),
        val favourites: Set<String> = emptySet(),
    )

    @Serializable
    private data class StoredReport(
        val added: Int,
        val updated: Int,
        val unmatched: List<String>,
        val failed: Int,
        val finishedAt: Long,
    )

    private companion object {
        const val TAG = "AniListLibrarySync"
        const val PREFS_NAME = "anilist_library_sync"
        const val KEY_USER = "user"
        const val KEY_SOURCE = "source"
        const val KEY_USE_ACCOUNT = "use_account"
        const val KEY_SKIP_NSFW = "skip_nsfw"
        const val KEY_AUTO = "auto"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_KNOWN_TITLES = "known_titles"
        const val KEY_STATE = "state"
        const val KEY_REPORT = "report"
        const val LOOKUP_CONCURRENCY = 3
        const val MIN_CONFIDENCE = 0.88
        const val MIN_MARGIN = 0.04
        const val RETRY_UNMATCHED_AFTER_MILLIS = 7L * 24 * 60 * 60 * 1000
        val json = Json { ignoreUnknownKeys = true }
        val matcher = TitleMatcher()
        val SEASON_PATTERNS = listOf(
            Regex("""(?:season|сезон)\s*(\d+)"""),
            Regex("""(\d+)(?:st|nd|rd|th)\s+season"""),
            Regex("""part\s*(\d+)"""),
            Regex("""[\p{L}]\s(\d)$"""),
        )
        val ROMAN = mapOf("ii" to 2, "iii" to 3, "iv" to 4)
    }
}
