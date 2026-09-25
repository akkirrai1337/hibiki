package org.akkirrai.hibiki.core.anilist

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.isWatchedToEnd
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository

/** One title the push would change on AniList. A null part is left as it is there. */
data class AniListPushItem(
    val titleId: String,
    val name: String,
    val mediaId: Int,
    val status: AniListMediaListStatus?,
    val statusCategory: LibraryCategory?,
    val progress: Int?,
    val favourite: Boolean,
)

data class AniListPushPlan(
    val items: List<AniListPushItem>,
    /** Titles left out, with the name to show: no confident match, or already set differently on AniList. */
    val skipped: List<String>,
)

data class AniListPushResult(val sent: Int, val failed: Int)

/**
 * One-way library sync, Hibiki to AniList, in two steps: [plan] works out what would change and writes
 * nothing, [execute] sends exactly that plan once the user has confirmed it.
 *
 * It is careful because it edits someone's account:
 * - it only ever adds or moves forward - progress is raised, never lowered, and nothing is removed;
 * - a status is sent only when the local one changed since the two sides last agreed; a title that is on
 *   AniList with a different status and has never been synced is left alone and listed as skipped;
 * - a title is matched to AniList by the same strict rules as the import (see [AniListLibrarySync]).
 */
class AniListLibraryPush(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val sync = AniListLibrarySync(appContext)
    private val library = LibraryRepository(appContext)
    private val watchState = WatchStateRepository(appContext)

    suspend fun plan(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): AniListPushPlan {
        val client = AndroidHttpClientFactory.create()
        try {
            val account = AniListRepository(appContext, client)
            val token = account.currentAccessToken() ?: throw AniListAuthenticationException()
            val api = AniListPublicLibrary(client, token)
            val viewer = account.getViewer().name
            val remote = api.library(viewer).associateBy { it.mediaId }
            val remoteFavourites = api.favourites(viewer).mapTo(mutableSetOf()) { it.mediaId }
            val linked = sync.linkedMediaIds()
            val pushed = loadPushed()
            val skipNsfw = sync.skipNsfw

            val local = library.getLibraryEntries()
                .groupBy { it.anime.id }
                .mapNotNull { (titleId, entries) ->
                    val categories = entries.map { it.category }.toSet()
                    val status = STATUS_ORDER.firstOrNull { it in categories }
                    val favourite = LibraryCategory.Favorite in categories
                    if (status == null && !favourite) return@mapNotNull null
                    if (skipNsfw && sync.isNsfwTitle(titleId)) return@mapNotNull null
                    Triple(entries.first().anime, status, favourite)
                }

            val skipped = mutableListOf<String>()
            val items = mutableListOf<AniListPushItem>()
            var done = 0
            val gate = Semaphore(LOOKUP_CONCURRENCY)
            onProgress(0, local.size)
            coroutineScope {
                local.map { (anime, status, favourite) ->
                    async {
                        val mediaId = linked[anime.id] ?: gate.withPermit { findOnAniList(api, anime) }
                        synchronized(this@AniListLibraryPush) {
                            done++
                            onProgress(done, local.size)
                        }
                        if (mediaId == null) {
                            synchronized(skipped) { skipped += anime.title }
                            return@async
                        }
                        val current = remote[mediaId]
                        val desired = status?.toAniList()
                        val baseline = pushed[mediaId.toString()] ?: sync.appliedCategory(mediaId)
                        val statusToSend = when {
                            desired == null -> null
                            current == null -> desired
                            current.status == desired -> null
                            // The local status is what both sides last agreed on, so it did not change here.
                            baseline == status?.storageValue -> null
                            baseline == null -> {
                                synchronized(skipped) { skipped += anime.title }
                                return@async
                            }
                            else -> desired
                        }
                        val watched = watchedEpisodes(anime.id)
                        val progressToSend = watched.takeIf { it > (current?.progress ?: 0) }
                        val favouriteToSend = favourite && mediaId !in remoteFavourites
                        if (statusToSend == null && progressToSend == null && !favouriteToSend) return@async
                        // Progress on a title that is not on the list yet needs a status to create the entry.
                        val effectiveStatus = statusToSend
                            ?: AniListMediaListStatus.CURRENT.takeIf { current == null && progressToSend != null }
                        synchronized(items) {
                            items += AniListPushItem(
                                titleId = anime.id,
                                name = anime.title,
                                mediaId = mediaId,
                                status = effectiveStatus,
                                statusCategory = if (statusToSend != null) status else null,
                                progress = progressToSend,
                                favourite = favouriteToSend,
                            )
                        }
                    }
                }.awaitAll()
            }
            return AniListPushPlan(items.sortedBy { it.name }, skipped.sorted())
        } finally {
            client.close()
        }
    }

    suspend fun execute(
        plan: AniListPushPlan,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): AniListPushResult {
        val client = AndroidHttpClientFactory.create()
        try {
            val token = AniListRepository(appContext, client).currentAccessToken()
                ?: throw AniListAuthenticationException()
            val api = AniListPublicLibrary(client, token)
            val pushed = loadPushed()
            var sent = 0
            var failed = 0
            var done = 0
            onProgress(0, plan.items.size)
            plan.items.chunked(BATCH_SIZE).forEach { batch ->
                val saves = batch.filter { it.status != null || it.progress != null }
                val ok = try {
                    if (saves.isNotEmpty()) api.saveEntries(saves.map { Triple(it.mediaId, it.status, it.progress) })
                    val favourites = batch.filter { it.favourite }
                    if (favourites.isNotEmpty()) api.toggleFavourites(favourites.map { it.mediaId })
                    true
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    AppLogger.w(TAG, "Pushing a batch to AniList failed", error)
                    false
                }
                if (ok) {
                    sent += batch.size
                    batch.forEach { item -> item.statusCategory?.let { pushed[item.mediaId.toString()] = it.storageValue } }
                } else {
                    failed += batch.size
                }
                done += batch.size
                onProgress(done, plan.items.size)
                // AniList allows about 90 requests a minute; batches are far below that, this only spaces them.
                kotlinx.coroutines.delay(BATCH_DELAY_MILLIS)
            }
            savePushed(pushed)
            return AniListPushResult(sent, failed)
        } finally {
            client.close()
        }
    }

    /** The highest episode finished, as AniList counts progress; zero when nothing is finished. */
    private fun watchedEpisodes(titleId: String): Int = watchState.getEpisodeProgress(titleId)
        .filter { it.isWatchedToEnd() }
        .maxOfOrNull { it.episodeNumber }
        ?.toInt() ?: 0

    private suspend fun findOnAniList(api: AniListPublicLibrary, anime: Anime): Int? = try {
        val names = (listOf(anime.title) + anime.alternativeTitles).filter(String::isNotBlank).distinct()
        val year = YEAR.find("${anime.releaseDate.orEmpty()} ${anime.subtitle}")?.value?.toIntOrNull()
        val local = AnimeTitle(
            id = anime.id,
            originalName = names.firstOrNull() ?: anime.title,
            synonyms = names.drop(1),
            year = year,
        )
        val candidates = names.take(2).flatMap { api.search(it) }.distinctBy { it.mediaId }
        sync.pickBestAniList(local, candidates)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        AppLogger.w(TAG, "AniList search failed for ${AnimeKey.parse(anime.id)?.nativeId}", error)
        null
    }

    private fun LibraryCategory.toAniList(): AniListMediaListStatus? = when (this) {
        LibraryCategory.Watching -> AniListMediaListStatus.CURRENT
        LibraryCategory.Planned -> AniListMediaListStatus.PLANNING
        LibraryCategory.Completed -> AniListMediaListStatus.COMPLETED
        LibraryCategory.Dropped -> AniListMediaListStatus.DROPPED
        LibraryCategory.OnHold -> AniListMediaListStatus.PAUSED
        LibraryCategory.Favorite, LibraryCategory.Saved -> null
    }

    private fun loadPushed(): MutableMap<String, String> = prefs.getString(KEY_PUSHED, null)
        ?.let { runCatching { json.decodeFromString<Pushed>(it).statuses }.getOrNull() }
        ?.toMutableMap() ?: mutableMapOf()

    private fun savePushed(statuses: Map<String, String>) {
        prefs.edit().putString(KEY_PUSHED, json.encodeToString(Pushed(statuses))).apply()
    }

    @Serializable
    private data class Pushed(val statuses: Map<String, String> = emptyMap())

    private companion object {
        const val TAG = "AniListLibraryPush"
        const val PREFS_NAME = "anilist_library_push"
        const val KEY_PUSHED = "pushed"
        const val LOOKUP_CONCURRENCY = 3
        const val BATCH_SIZE = 10
        const val BATCH_DELAY_MILLIS = 800L
        val json = Json { ignoreUnknownKeys = true }
        val YEAR = Regex("""(?:19|20)\d{2}""")
        val STATUS_ORDER = listOf(
            LibraryCategory.Watching,
            LibraryCategory.Completed,
            LibraryCategory.OnHold,
            LibraryCategory.Dropped,
            LibraryCategory.Planned,
        )
    }
}
