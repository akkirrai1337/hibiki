package org.akkirrai.hibiki.core.source

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.health.SourceHealthReporter
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.SearchFilterOption
import org.akkirrai.hibiki.app.settings.AppPreferences
import java.util.concurrent.ConcurrentHashMap

class AnimeSourceRuntime internal constructor(
    val descriptor: AnimeSourceDescriptor,
    val source: AnimeSource,
    private val localizeFilters: (AnimeSearchFilterCatalog, Boolean) -> AnimeSearchFilterCatalog,
    private val normalizeTitleId: (String) -> String,
    /** Status names shown in the status filter, from string resources in the app language. */
    private val statusLabel: (AnimeReleaseStatus) -> String = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
) {
    private val latestSource = source as? LatestSource
    private val playbackSource = source as? PlaybackSource
    // Catalog/details and playback both need the full provider title. Reusing it here lets a
    // direct Details -> Player transition avoid a second getById() request even though the two
    // features use different repositories.
    private val detailsCache = ConcurrentHashMap<String, CachedDetails>()
    private val detailsLocks = ConcurrentHashMap<String, Mutex>()

    val supportsPlayback: Boolean
        get() = playbackSource != null

    suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> =
        source.search(source.catalogCapabilities.adapt(request)).map(::scopeTitle)

    suspend fun search(query: String): List<AnimeTitle> = source.search(query).map(::scopeTitle)

    suspend fun details(id: String): AnimeTitle {
        val nativeId = nativeId(id)
        cachedDetails(nativeId)?.let { return it }
        val lock = detailsLocks.computeIfAbsent(nativeId) { Mutex() }
        return lock.withLock {
            cachedDetails(nativeId) ?: source.getById(nativeId)
                .let(::scopeTitle)
                .also { detailsCache[nativeId] = CachedDetails(it, System.currentTimeMillis()) }
        }.also {
            detailsLocks.remove(nativeId, lock)
        }
    }

    fun normalizeId(id: String): String = scopedId(nativeId(id))

    suspend fun latest(limit: Int): List<AnimeTitle> {
        return latestSource?.latest(limit)?.map(::scopeTitle).orEmpty()
    }

    suspend fun filterCatalog(preferEnglish: Boolean): AnimeSearchFilterCatalog =
        localizeFilters(source.getSearchFilterCatalog(), preferEnglish).sanitized(preferEnglish, statusLabel)

    internal suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> =
        playbackSource?.getPlaybackGroups(title.copy(id = nativeId(title.id))).orEmpty()

    internal suspend fun getPlayerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink> = playbackSource?.getPlayerLinks(
        title = title.copy(id = nativeId(title.id)),
        group = group,
        episode = episode,
    ).orEmpty()

    private fun nativeId(id: String): String {
        val scoped = AnimeKey.parse(id)
        if (scoped != null) {
            require(scoped.sourceId == descriptor.id) {
                "Title $id belongs to ${scoped.sourceId}, not ${descriptor.id}"
            }
            return scoped.nativeId
        }
        return normalizeTitleId(id)
    }

    private fun scopedId(nativeId: String): String = AnimeKey(descriptor.id, nativeId).value

    private fun cachedDetails(nativeId: String): AnimeTitle? {
        val cached = detailsCache[nativeId] ?: return null
        if (System.currentTimeMillis() - cached.cachedAt >= DETAILS_CACHE_TTL_MS) {
            detailsCache.remove(nativeId, cached)
            return null
        }
        return cached.title
    }

    internal fun cachedDetailsFor(id: String): AnimeTitle? = cachedDetails(nativeId(id))

    private fun scopeTitle(title: AnimeTitle): AnimeTitle = title.copy(
        id = scopedId(title.id),
        similarAnime = title.similarAnime.map { it.copy(id = scopedId(it.id)) },
        franchiseAnime = title.franchiseAnime.map { it.copy(id = scopedId(it.id)) },
        relatedAnime = title.relatedAnime.map { it.copy(id = scopedId(it.id)) },
    )

    private data class CachedDetails(
        val title: AnimeTitle,
        val cachedAt: Long,
    )

    private companion object {
        const val DETAILS_CACHE_TTL_MS = 30 * 60_000L
    }
}

class AnimeSourceRuntimeManager(
    context: Context,
    private val client: HttpClient,
    private val sourceHealthReporter: SourceHealthReporter = HibikiSourceHealth.store.reporter,
) {
    private val appContext = context.applicationContext
    private val runtimes = ConcurrentHashMap<SourceId, CachedRuntime>()

    val selectedId: SourceId
        get() = AppPreferences.readState(appContext).animeSource

    fun current(): AnimeSourceRuntime = runtime(selectedId)

    fun forTitle(titleId: String): AnimeSourceRuntime =
        AnimeKey.parse(titleId)?.sourceId?.let(::runtime)
            // Unscoped identifiers were persisted by versions which only supported YummyAnime.
            ?: runtime(AppPreferences.DEFAULT_ANIME_SOURCE_ID)

    // This manager instance (owned by a repository, which in turn outlives a lot of screen
    // navigation) can hold a runtime for an extension that has since been updated or removed.
    // Comparing against AnimeSourceRegistry's generation counter (bumped whenever the loaded
    // sources change) means a new version takes effect on the very next call instead of
    // requiring an app restart.
    fun runtime(sourceId: SourceId): AnimeSourceRuntime {
        val generation = AnimeSourceRegistry.extensionGeneration
        runtimes[sourceId]?.let { cached -> if (cached.generation == generation) return cached.runtime }
        val fresh = AnimeSourceRegistry.createRuntime(appContext, sourceId)
        runtimes[sourceId] = CachedRuntime(fresh, generation)
        return fresh
    }

    private data class CachedRuntime(val runtime: AnimeSourceRuntime, val generation: Int)
}

private fun AnimeSearchFilterCatalog.sanitized(
    preferEnglish: Boolean,
    statusLabel: (AnimeReleaseStatus) -> String,
): AnimeSearchFilterCatalog = copy(
    sortOptions = sortOptions.sanitizeOptions(preferEnglish),
    typeOptions = typeOptions.takeIf { capabilities.supports(org.akkirrai.beakokit.model.AnimeSearchFilter.TYPE) }
        .orEmpty().sanitizeOptions(preferEnglish),
    statusOptions = statusOptions.takeIf { capabilities.supports(org.akkirrai.beakokit.model.AnimeSearchFilter.STATUS) }
        .orEmpty().sanitizeOptions(preferEnglish, statusLabel = statusLabel),
    genreOptions = genreOptions.takeIf {
        capabilities.supports(org.akkirrai.beakokit.model.AnimeSearchFilter.INCLUDED_GENRES) ||
            capabilities.supports(org.akkirrai.beakokit.model.AnimeSearchFilter.EXCLUDED_GENRES)
    }.orEmpty().sanitizeOptions(preferEnglish),
)

private fun List<SearchFilterOption>.sanitizeOptions(
    preferEnglish: Boolean,
    statusLabel: ((AnimeReleaseStatus) -> String)? = null,
): List<SearchFilterOption> = mapNotNull { option ->
    val id = option.id.trim()
    if (id.isBlank()) return@mapNotNull null
    val rawTitle = option.title.trim()
    val title = when {
        statusLabel != null -> canonicalStatusLabel(id, rawTitle, statusLabel)
        rawTitle.isBlank() || rawTitle == id -> id.humanizedAlias()
        else -> rawTitle
    }
    // A numeric API identifier is not a user-facing label. A source with no label simply does
    // not expose that filter until its adapter supplies one.
    title.takeUnless { it.isBlank() || it.all(Char::isDigit) }
        ?.let { option.copy(id = id, title = it) }
}.distinctBy(SearchFilterOption::id)

/** A recognised status gets its resource label; an unrecognised one keeps the source's own words. */
private fun canonicalStatusLabel(id: String, title: String, statusLabel: (AnimeReleaseStatus) -> String): String {
    val status = listOf(id, title)
        .map(AnimeReleaseStatus::from)
        .firstOrNull { it != AnimeReleaseStatus.UNKNOWN }
    return status?.let(statusLabel) ?: title.takeIf(String::isNotBlank) ?: id.humanizedAlias()
}

private fun String.humanizedAlias(): String =
    replace('-', ' ')
        .replace('_', ' ')
        .lowercase()
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
