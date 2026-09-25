package org.akkirrai.hibiki.core.source

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import org.akkirrai.beakokit.matching.TitleMatcher
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.hibiki.core.model.ReleaseStatusText
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.AnimeTrailerTitle
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeRating
import org.akkirrai.hibiki.core.model.AnimeTrailer
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.network.hasActiveInternetConnection
import java.util.concurrent.ConcurrentHashMap

class AnimeSearchRepository(
    context: Context? = null,
    private val client: HttpClient = AndroidHttpClientFactory.create(),
    sourceManager: AnimeSourceRuntimeManager? = null,
    private val closeClientOnClose: Boolean = true,
) {
    private val searchCache = ConcurrentHashMap<String, CachedSearchResults>()
    private val filterCatalogCache = ConcurrentHashMap<String, AnimeSearchFilterCatalog>()
    private val appContext = context?.applicationContext
    private val appPreferences = appContext?.let(::AppPreferences)
    private val sourceManager = sourceManager ?: appContext?.let { AnimeSourceRuntimeManager(it, client) }
    private val titleMatcher = TitleMatcher()
    private val detailsRequestSlots = Semaphore(MAX_CONCURRENT_DETAILS_REQUESTS)
    suspend fun search(query: String): List<Anime> {
        return search(query = query, limit = SEARCH_PAGE_SIZE, offset = 0)
    }

    /**
     * [allowEmptyQuery] distinguishes a deliberate "browse everything" request (Catalog, with no
     * query and no filters) from the search bar's idle state (nothing typed, nothing filtered -
     * home/search screens rely on getting `emptyList()` back there rather than a full listing).
     */
    suspend fun search(
        request: AnimeSearchRequest,
        allowEmptyQuery: Boolean = false,
        forceRefresh: Boolean = false,
    ): List<Anime> {
        val normalizedQuery = request.query.trim()
        val hasFilters = request.typeAliases.isNotEmpty() ||
            request.statusAliases.isNotEmpty() ||
            request.includedGenreAliases.isNotEmpty() ||
            request.excludedGenreAliases.isNotEmpty() ||
            request.yearFrom != null ||
            request.yearTo != null ||
            request.sourceFilterValues.isNotEmpty() ||
            request.sort != AnimeSearchSort.RELEVANCE
        if (normalizedQuery.isBlank() && !hasFilters && !allowEmptyQuery) return emptyList()

        val normalizedRequest = request.copy(query = normalizedQuery)
        val cacheKey = searchCacheKey(normalizedRequest)
        if (!forceRefresh) {
            getCachedSearch(cacheKey)?.let { cached ->
                AppLogger.d(TAG, "search: cache hit items=${cached.size}")
                return cached
            }
        }

        ensureInternetConnection()

        val preferEnglish = preferEnglish()
        val source = currentSource()
        val sourceId = source.descriptor.id.value
        // The query is logged by length only - it is what the user typed.
        val requestSummary = "source=$sourceId queryLength=${normalizedQuery.length} filtered=$hasFilters " +
            "sort=${normalizedRequest.sort} limit=${normalizedRequest.limit} offset=${normalizedRequest.offset}"
        val startedAt = System.currentTimeMillis()
        AppLogger.d(TAG, "search: start $requestSummary")
        val sourceTitles = try {
            source.search(normalizedRequest)
        } catch (error: CancellationException) {
            AppLogger.d(TAG, "search: cancelled after ${System.currentTimeMillis() - startedAt}ms $requestSummary")
            throw error
        } catch (error: Throwable) {
            AppLogger.w(TAG, "search: source failed in ${System.currentTimeMillis() - startedAt}ms $requestSummary", error)
            throw error
        }
        AppLogger.d(TAG, "search: source returned ${sourceTitles.size} in ${System.currentTimeMillis() - startedAt}ms $requestSummary")
        val results = sourceTitles.map { title ->
            (getCachedDetails(detailsCacheKey(title.id)) ?: title.toAnime(preferEnglish = preferEnglish))
                .copy(title = title.displayName)
        }

        searchCache[cacheKey] = CachedSearchResults(
            items = results,
            cachedAt = System.currentTimeMillis(),
        )
        trimOldestEntries(searchCache, MAX_SEARCH_CACHE_ENTRIES) { it.cachedAt }
        return results
    }

    /**
     * A search on one specific source rather than the selected one, returning the source's own title
     * (for matching on its names, year and episode count) next to the card the app would show for it.
     * Not cached: it is used for one-off lookups such as library sync.
     */
    suspend fun findOnSource(sourceId: SourceId, query: String, limit: Int = 8): List<Pair<AnimeTitle, Anime>> {
        ensureInternetConnection()
        val runtime = sourceManager?.runtime(sourceId)
            ?: error("Anime source selection requires an Android context")
        val preferEnglish = preferEnglish()
        return runtime.search(
            AnimeSearchRequest(query = query.trim(), limit = limit, offset = 0, sort = AnimeSearchSort.RELEVANCE),
        ).map { title -> title to title.toAnime(preferEnglish = preferEnglish).copy(title = title.displayName) }
    }

    suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog {
        val source = currentSource()
        val preferEnglish = preferEnglish()
        val key = "${source.descriptor.id.value}:$preferEnglish"
        return filterCatalogCache[key]
            ?: source.filterCatalog(preferEnglish).also { filterCatalogCache[key] = it }
    }

    suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
    ): List<Anime> {
        return search(
            AnimeSearchRequest(
                query = query,
                limit = limit,
                offset = offset,
                sort = AnimeSearchSort.RELEVANCE,
            ),
        )
    }

    /**
     * The source's own "latest releases" list, converted exactly like a search page and cached the
     * same way, so a card shows the same fields wherever it appears.
     */
    suspend fun latest(
        limit: Int,
        forceRefresh: Boolean = false,
    ): List<Anime> {
        val cacheKey = "latest:${selectedSourceId().value}:$limit:${languageKey()}"
        if (!forceRefresh) getCachedSearch(cacheKey)?.let { return it }

        ensureInternetConnection()

        val preferEnglish = preferEnglish()
        val source = currentSource()
        val results = source.latest(limit).map { title ->
            (getCachedDetails(detailsCacheKey(title.id)) ?: title.toAnime(preferEnglish = preferEnglish))
                .copy(title = title.displayName)
        }
        searchCache[cacheKey] = CachedSearchResults(
            items = results,
            cachedAt = System.currentTimeMillis(),
        )
        trimOldestEntries(searchCache, MAX_SEARCH_CACHE_ENTRIES) { it.cachedAt }
        return results
    }

    suspend fun getDetails(
        id: String,
        fallback: Anime,
        requireSourceDetails: Boolean = false,
    ): Anime {
        AppLogger.d(TAG, "getDetails(id=$id, fallback.title=${fallback.title.take(50)})")
        val cacheKey = detailsCacheKey(id)
        getCachedDetails(cacheKey)?.let {
            AppLogger.d(TAG, "getDetails: cache hit for $cacheKey")
            return it
        }

        val detailsMutex = detailsMutexes.computeIfAbsent(cacheKey) { Mutex() }
        return try {
            detailsMutex.withLock {
                getCachedDetails(cacheKey)?.let { return@withLock it }

                detailsRequestSlots.withPermit {
                    getCachedDetails(cacheKey)?.let { return@withPermit it }

                    ensureInternetConnection()

                    val source = sourceManager?.forTitle(id) ?: currentSource()
                    val title = runCatching { source.details(id) }
                        .getOrElse {
                        if (requireSourceDetails) throw it
                        // A source.details() failure here is otherwise completely silent: the
                        // fallback below quietly serves sparse search-card data (no status,
                        // description, or episode count) instead of surfacing an error, which
                        // makes a genuine source bug look like "this title just has no details".
                        AppLogger.w(TAG, "getDetails: source.details failed for $id, falling back to search", it)
                            source.search(fallback.title)
                                .bestMatchFor(fallback.title)
                                ?: throw it
                        }
                    val trailer = title.trailer?.toAnimeTrailer()
                    val anime = title.toAnime(
                        canonicalId = title.id,
                        preferEnglish = preferEnglish(),
                        fallback = fallback,
                        trailer = trailer ?: fallback.trailer,
                    )

                    detailsCache[cacheKey] = CachedAnime(
                        anime = anime,
                        cachedAt = System.currentTimeMillis(),
                    )
                    trimOldestEntries(detailsCache, MAX_DETAILS_CACHE_ENTRIES) { it.cachedAt }
                    anime
                }
            }
        } finally {
            detailsMutexes.remove(cacheKey, detailsMutex)
        }
    }

    fun clearCaches() {
        searchCache.clear()
        filterCatalogCache.clear()
        detailsCache.clear()
        detailsMutexes.clear()
    }

    fun close() {
        searchCache.clear()
        filterCatalogCache.clear()
        if (closeClientOnClose) client.close()
    }

    private fun AnimeTitle.toAnime(
        canonicalId: String = id,
        preferEnglish: Boolean,
        fallback: Anime? = null,
        trailer: AnimeTrailer? = null,
    ): Anime {
        val posterUrl = posterUrl ?: fallback?.posterUrl
        val sourcePosterFallbackUrl = posterFallbackUrl
            ?.takeIf { it.isNotBlank() && it != posterUrl }
        val statusLabel = appContext?.let(releaseStatus::localizedDisplayName)
            ?: releaseStatus.name.lowercase().replaceFirstChar(Char::uppercase)
        val resolvedStatus = statusLabel
            .takeUnless { releaseStatus == AnimeReleaseStatus.UNKNOWN }
            ?: fallback?.status
            ?: statusLabel
        return Anime(
            id = canonicalId,
            title = displayName,
            subtitle = buildSubtitle(fallback?.subtitle),
            episodesLabel = if (resolvedStatus.isAnnouncementStatus()) {
                if (preferEnglish) "announcement" else "анонс"
            } else {
                buildEpisodesLabel(fallback?.episodesLabel, preferEnglish)
            },
            status = resolvedStatus,
            nextEpisodeAt = nextEpisodeAt ?: fallback?.nextEpisodeAt,
            posterUrl = posterUrl,
            posterFallbackUrl = sourcePosterFallbackUrl ?: fallback?.posterFallbackUrl
                ?.takeIf { it.isNotBlank() && it != posterUrl },
            bannerUrl = bannerUrl ?: fallback?.bannerUrl,
            description = description ?: fallback?.description,
            genres = genres.ifEmpty { fallback?.genres.orEmpty() },
            alternativeTitles = buildAlternativeTitles(fallback?.alternativeTitles.orEmpty()),
            ratings = ratings.map { rating ->
                AnimeRating(
                    source = rating.source,
                    value = rating.value,
                    votes = rating.votes,
                )
            }.ifEmpty { fallback?.ratings.orEmpty() },
            ageRating = ageRating ?: fallback?.ageRating,
            viewCount = viewCount ?: fallback?.viewCount,
            screenshots = screenshots.ifEmpty { fallback?.screenshots.orEmpty() },
            trailer = trailer,
            sourceMaterial = sourceMaterial ?: fallback?.sourceMaterial,
            studios = studios.ifEmpty { fallback?.studios.orEmpty() },
            producers = producers.ifEmpty { fallback?.producers.orEmpty() },
            releaseDate = formatReleaseDate(preferEnglish) ?: fallback?.releaseDate,
        )
    }

    private fun AnimeTrailerTitle.toAnimeTrailer(): AnimeTrailer {
        return AnimeTrailer(
            id = id,
            site = site,
            thumbnailUrl = thumbnailUrl,
            sourceUrl = sourceUrl,
        )
    }

    private fun AnimeTitle.buildAlternativeTitles(fallbackTitles: List<String>): List<String> {
        val primaryTitle = displayName
        return buildList {
            russianName?.let(::add)
            englishName?.let(::add)
            originalName.let(::add)
            japaneseName?.let(::add)
            addAll(synonyms)
            addAll(fallbackTitles)
        }
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .filterNot { it.equals(primaryTitle, ignoreCase = true) }
    }

    private fun AnimeTitle.buildSubtitle(fallbackSubtitle: String?): String {
        val parts = listOfNotNull(
            type?.toDisplayType(),
            year?.toString(),
        )
        return parts.joinToString(" · ").ifBlank { fallbackSubtitle.orEmpty() }
    }

    private fun AnimeTitle.formatReleaseDate(preferEnglish: Boolean): String? {
        val releaseYear = year ?: return null
        val seasonTitle = season?.toSeasonTitle(preferEnglish)
        return listOfNotNull(seasonTitle, releaseYear.toString()).joinToString(" ")
    }

    private fun Int.toSeasonTitle(preferEnglish: Boolean): String? {
        return when (this) {
            1 -> if (preferEnglish) "Winter" else "Зима"
            2 -> if (preferEnglish) "Spring" else "Весна"
            3 -> if (preferEnglish) "Summer" else "Лето"
            4 -> if (preferEnglish) "Autumn" else "Осень"
            else -> null
        }
    }

    private fun String?.isAnnouncementStatus(): Boolean {
        return ReleaseStatusText.parse(this) == AnimeReleaseStatus.ANNOUNCEMENT
    }

    private fun AnimeTitle.buildEpisodesLabel(
        fallbackLabel: String?,
        preferEnglish: Boolean,
    ): String {
        // The count of what is actually playable is the number a viewer is deciding on, so it wins
        // whenever the source publishes one. The announced total is still real information when it
        // does not - "unknown" helps nobody - but a show that has not finished airing has fewer
        // episodes up than that total, so it is labelled as a total rather than left to be read as
        // a promise.
        availableEpisodeCount?.let { count -> return "$count ${episodesWord(count, preferEnglish)}" }
        val total = episodeCount
        return when {
            total == null -> fallbackLabel.orEmpty().ifBlank {
                if (preferEnglish) "Episodes unknown" else "Количество серий неизвестно"
            }
            releaseStatus == AnimeReleaseStatus.RELEASED -> "$total ${episodesWord(total, preferEnglish)}"
            preferEnglish -> "$total ${episodesWord(total, true)} total"
            else -> "Всего $total ${episodesWord(total, false)}"
        }
    }

    private fun episodesWord(count: Int, preferEnglish: Boolean): String {
        if (preferEnglish) return if (count == 1) "episode" else "episodes"
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> "серий"
            mod10 == 1 -> "серия"
            mod10 in 2..4 -> "серии"
            else -> "серий"
        }
    }

    private fun List<AnimeTitle>.bestMatchFor(queryTitle: String): AnimeTitle? {
        val probe = AnimeTitle(
            id = "",
            russianName = queryTitle,
            englishName = queryTitle,
            originalName = queryTitle,
            japaneseName = null,
            synonyms = emptyList(),
            year = null,
            type = null,
            episodeCount = null,
            posterUrl = null,
            status = null,
            description = null,
        )
        return asSequence()
            .map { candidate ->
                candidate to titleMatcher.confidence(
                    title = probe,
                    candidateNames = candidate.allNames(),
                    candidateYear = candidate.year,
                    candidateType = candidate.type,
                    candidateEpisodes = candidate.episodeCount,
                )
            }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= LEGACY_ID_MATCH_CONFIDENCE }
            ?.first
    }

    /** Whether titles should read in English for the current language setting - the catalog asks so
     * an aggregator entry is named the same way a source title on the same screen would be. */
    fun prefersEnglishTitles(): Boolean = preferEnglish()

    private fun preferEnglish(): Boolean {
        return when (appPreferences?.state?.value?.languageMode ?: LanguageMode.SYSTEM) {
            LanguageMode.ENGLISH, LanguageMode.UKRAINIAN -> true
            LanguageMode.RUSSIAN -> false
            LanguageMode.SYSTEM -> appContext?.resources?.configuration?.locales?.get(0)?.language != "ru"
        }
    }

    private fun ensureInternetConnection() {
        val context = appContext ?: return
        if (!hasActiveInternetConnection(context)) {
            throw NoInternetConnectionException(context.getString(org.akkirrai.hibiki.R.string.home_error_no_internet))
        }
    }

    /**
     * The app language as it appears in cache keys - one place, so a key built for a list ("latest")
     * can never disagree with one built for a search page.
     */
    private fun languageKey(): String =
        when (appPreferences?.state?.value?.languageMode ?: LanguageMode.SYSTEM) {
            LanguageMode.UKRAINIAN -> "uk"
            LanguageMode.ENGLISH -> "en"
            LanguageMode.RUSSIAN -> "ru"
            LanguageMode.SYSTEM -> "sys"
        }

    private fun searchCacheKey(request: AnimeSearchRequest): String {
        val languageKey = languageKey()
        val types = request.typeAliases.sorted().joinToString(",")
        val statuses = request.statusAliases.sorted().joinToString(",")
        val includedGenres = request.includedGenreAliases.sorted().joinToString(",")
        val excludedGenres = request.excludedGenreAliases.sorted().joinToString(",")
        return buildString {
            append(SEARCH_CACHE_VERSION)
            append(':')
            append(AnimeSourceRegistry.extensionGeneration)
            append(':')
            append(selectedSourceId().value)
            append(':')
            append(languageKey)
            append(':')
            append(request.query.lowercase())
            append(':')
            append(request.limit)
            append(':')
            append(request.offset)
            append(':')
            append(request.sort.name)
            append(':')
            append(types)
            append(':')
            append(statuses)
            append(':')
            append(includedGenres)
            append(':')
            append(excludedGenres)
            append(':')
            append(request.yearFrom ?: "")
            append(':')
            append(request.yearTo ?: "")
            append(':')
            append(request.sourceFilterValues.toSortedMap().entries.joinToString("|") { "${it.key}=${it.value}" })
        }
    }

    private fun detailsCacheKey(id: String): String {
        val languageKey = when (appPreferences?.state?.value?.languageMode ?: LanguageMode.SYSTEM) {
            LanguageMode.UKRAINIAN -> "uk"
            LanguageMode.ENGLISH -> "en"
            LanguageMode.RUSSIAN -> "ru"
            LanguageMode.SYSTEM -> "sys"
        }
        val sourceId = sourceManager?.forTitle(id)?.descriptor?.id ?: selectedSourceId()
        return "$DETAILS_CACHE_VERSION:${AnimeSourceRegistry.extensionGeneration}:${sourceId.value}:$languageKey:$id"
    }

    private fun selectedSourceId() = sourceManager?.selectedId
        ?: error("Anime source selection requires an Android context")

    private fun currentSource(): AnimeSourceRuntime = sourceManager?.current()
        ?: error("Anime source selection requires an Android context")

    private fun getCachedSearch(key: String): List<Anime>? {
        val cached = searchCache[key] ?: return null
        if (System.currentTimeMillis() - cached.cachedAt >= SEARCH_CACHE_TTL_MS) {
            searchCache.remove(key, cached)
            return null
        }
        return cached.items
    }

    private fun getCachedDetails(key: String): Anime? {
        val cached = detailsCache[key] ?: return null
        if (System.currentTimeMillis() - cached.cachedAt >= DETAILS_CACHE_TTL_MS) {
            detailsCache.remove(key, cached)
            return null
        }
        return cached.anime
    }

    private fun <Value> trimOldestEntries(
        cache: ConcurrentHashMap<String, Value>,
        maxEntries: Int,
        cachedAt: (Value) -> Long,
    ) {
        val overflow = cache.size - maxEntries
        if (overflow <= 0) return
        cache.entries
            .sortedBy { cachedAt(it.value) }
            .take(overflow)
            .forEach { (key, value) -> cache.remove(key, value) }
    }

    private fun String.toDisplayType(): String {
        return when (uppercase()) {
            "TV" -> "TV"
            "TV_SHORT" -> "TV Short"
            "OVA" -> "OVA"
            "ONA" -> "ONA"
            "MOVIE" -> "Movie"
            "SHORT_MOVIE", "SHORT-MOVIE" -> "Short Movie"
            "SPECIAL" -> "Special"
            else -> replace("_", " ").replace("-", " ")
                .replaceFirstChar { it.uppercase() }
        }
    }

    private data class CachedSearchResults(
        val items: List<Anime>,
        val cachedAt: Long,
    )

    private data class CachedAnime(
        val anime: Anime,
        val cachedAt: Long,
    )

    private companion object {
        const val TAG = "AnimeSearchRepository"
        const val SEARCH_CACHE_VERSION = 2
        const val SEARCH_PAGE_SIZE = 20
        const val MAX_CONCURRENT_DETAILS_REQUESTS = 3
        const val MAX_SEARCH_CACHE_ENTRIES = 100
        const val MAX_DETAILS_CACHE_ENTRIES = 200
        const val SEARCH_CACHE_TTL_MS = 5 * 60_000L
        const val DETAILS_CACHE_TTL_MS = 30 * 60_000L
        const val DETAILS_CACHE_VERSION = 1
        const val LEGACY_ID_MATCH_CONFIDENCE = 0.72

        /**
         * Detail screens use short-lived repository instances, but a title's fully resolved
         * metadata is valid across those instances. Keeping it here prevents a second network
         * request when the user returns to a recently opened title.
         */
        val detailsCache = ConcurrentHashMap<String, CachedAnime>()
        val detailsMutexes = ConcurrentHashMap<String, Mutex>()
    }
}
