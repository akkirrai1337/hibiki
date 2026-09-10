package org.akkirrai.hibiki.core.source

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.metadata.ExternalMetadataPreferences
import org.akkirrai.beakokit.metadata.MetadataProviderId
import org.akkirrai.beakokit.metadata.ExternalMetadataService
import org.akkirrai.beakokit.metadata.mergeExternalMetadata
import org.akkirrai.beakokit.metadata.metadataProviderOrder
import org.akkirrai.hibiki.core.metadata.PreferencesExternalMetadataStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import org.akkirrai.beakokit.matching.TitleMatcher
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.AnimeTrailerTitle
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeRating
import org.akkirrai.hibiki.core.model.AnimeTrailer
import org.akkirrai.hibiki.core.model.RelatedAnime
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.network.hasActiveInternetConnection
import java.util.concurrent.ConcurrentHashMap

class AnimeSearchRepository(
    context: Context? = null,
    private val client: HttpClient = AndroidHttpClientFactory.create(),
    sourceManager: AnimeSourceRuntimeManager? = null,
    private val closeClientOnClose: Boolean = true,
    /** Shared with the rest of the app when there is one - see HibikiDependencies. Constructed here
     * only for the standalone paths that build this repository on their own. */
    metadataService: ExternalMetadataService? = null,
) {
    private val searchCache = ConcurrentHashMap<String, CachedSearchResults>()
    private val appContext = context?.applicationContext
    private val appPreferences = appContext?.let(::AppPreferences)
    private val sourceManager = sourceManager ?: appContext?.let { AnimeSourceRuntimeManager(it, client) }
    private val titleMatcher = TitleMatcher()
    // Built here rather than injected: everything it needs (the shared client, the app's own
    // preferences) is already on this repository, and nothing else in the app describes a title.
    private val metadataService = metadataService
        ?: appContext?.let { ExternalMetadataService(client, PreferencesExternalMetadataStore(it)) }
    // Background matching outlives the request that started it on purpose: the screen has painted,
    // and what this fills in is for the next visit. Cancelled with the repository.
    private val metadataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
            request.sort != AnimeSearchSort.RELEVANCE
        if (normalizedQuery.isBlank() && !hasFilters && !allowEmptyQuery) return emptyList()

        val normalizedRequest = request.copy(query = normalizedQuery)
        val cacheKey = searchCacheKey(normalizedRequest)
        if (!forceRefresh) {
            getCachedSearch(cacheKey)?.let { return it }
        }

        ensureInternetConnection()

        val preferEnglish = preferEnglish()
        val source = currentSource()
        val results = describeAll(source, source.search(normalizedRequest))
            .map { title ->
                getCachedDetails(detailsCacheKey(title.id))
                    ?: title.toAnime(preferEnglish = preferEnglish)
            }

        searchCache[cacheKey] = CachedSearchResults(
            items = results,
            cachedAt = System.currentTimeMillis(),
        )
        trimOldestEntries(searchCache, MAX_SEARCH_CACHE_ENTRIES) { it.cachedAt }
        return results
    }

    suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog {
        return currentSource().filterCatalog(preferEnglish())
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
            )
        )
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
                    val described = describe(source, title)
                    val trailer = described.trailer?.toAnimeTrailer()
                    val anime = described.toAnime(
                        canonicalId = described.id,
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
        detailsCache.clear()
        detailsMutexes.clear()
    }

    fun close() {
        searchCache.clear()
        metadataScope.cancel()
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
        val resolvedStatus = releaseStatus.localizedDisplayName(preferEnglish)
            .takeUnless { releaseStatus == AnimeReleaseStatus.UNKNOWN }
            ?: fallback?.status
            ?: if (preferEnglish) "Unknown" else "Неизвестно"
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
            similarAnime = similarAnime.map(RelatedAnimeTitleMapper::map)
                .ifEmpty { fallback?.similarAnime.orEmpty() },
            franchiseAnime = franchiseAnime.map(RelatedAnimeTitleMapper::map)
                .ifEmpty { fallback?.franchiseAnime.orEmpty() },
            relatedAnime = relatedAnime.map(RelatedAnimeTitleMapper::map)
                .ifEmpty { fallback?.relatedAnime.orEmpty() },
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
        val normalized = orEmpty().trim().lowercase()
        return normalized == "анонс" || normalized == "announcement" || normalized == "announced" || normalized == "anons"
    }

    private fun AnimeTitle.buildEpisodesLabel(
        fallbackLabel: String?,
        preferEnglish: Boolean,
    ): String {
        // The available count is the most useful number while a show is airing; when a source does
        // not publish it, the announced total (including one supplied by the metadata aggregator)
        // is still real information and must not be rendered as "unknown" merely because the show
        // is ongoing or announced.
        val releasedCount = availableEpisodeCount ?: episodeCount
        return when (val count = releasedCount) {
            null -> fallbackLabel.orEmpty().ifBlank {
                if (preferEnglish) "Episodes unknown" else "Количество серий неизвестно"
            }
            else -> "$count ${episodesWord(count, preferEnglish)}"
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

    /**
     * Replaces a title's descriptive fields with a metadata provider's, when both the source asked
     * for that in its manifest and the user has not turned it off.
     *
     * Failures are swallowed on purpose: a provider being unreachable, rate-limiting us, or simply
     * not carrying this title must cost the better description and nothing else - the source's own
     * screen still renders exactly as it did before this existed.
     */
    /**
     * Describes a whole list, or none of it.
     *
     * All at once matters: a grid where some cards carry the provider's name and poster while their
     * neighbours carry the source's reads as a broken list, even when every entry in it is correct.
     * So a screen is described only when every title on it is already in the store - which costs
     * nothing - and otherwise stays entirely the source's while the missing ones are matched in the
     * background, ready for the next visit. Describing them inline would be a request per unseen
     * title at roughly one a second, on a screen built to be scrolled.
     */
    private suspend fun describeAll(source: AnimeSourceRuntime, titles: List<AnimeTitle>): List<AnimeTitle> {
        val service = metadataService ?: return titles
        val order = providerOrderFor(source)
        if (order.isEmpty() || titles.isEmpty()) return titles

        val cached = titles.map { service.cachedMetadataFor(it.id, order) }
        if (cached.all { it != null }) {
            return titles.mapIndexed { index, title -> mergeExternalMetadata(title, cached[index]) }
        }
        warmMetadata(service, order, titles.filterIndexed { index, _ -> cached[index] == null })
        return titles
    }

    /** Matches what a list screen showed but could not describe, so the next visit can. One at a
     * time and off the caller's coroutine: the queues inside the service pace these anyway, and the
     * screen that asked has already painted. */
    private fun warmMetadata(service: ExternalMetadataService, order: List<MetadataProviderId>, titles: List<AnimeTitle>) {
        if (titles.isEmpty()) return
        metadataScope.launch {
            for (title in titles) {
                runCatching { service.metadataFor(title, order) }
                    .onFailure { AppLogger.w(TAG, "warmMetadata: ${title.id} not described", it) }
            }
        }
    }

    private fun providerOrderFor(source: AnimeSourceRuntime): List<MetadataProviderId> {
        val preferences = appPreferences?.state?.value ?: return emptyList()
        return metadataProviderOrder(
            ExternalMetadataPreferences(
                enabled = preferences.externalMetadataEnabled,
                overrides = preferences.externalMetadataOverrides,
                provider = preferences.externalMetadataProvider,
                fallbackEnabled = preferences.externalMetadataFallback,
            ),
            source.descriptor.id.value,
            source.descriptor.info.useExternalMetadata,
        )
    }

    private suspend fun describe(source: AnimeSourceRuntime, title: AnimeTitle): AnimeTitle {
        val service = metadataService ?: return title
        val order = providerOrderFor(source)
        if (order.isEmpty()) return title
        val external = runCatching { service.metadataFor(title, order) }
            .onFailure { AppLogger.w(TAG, "describe: metadata lookup failed for ${title.id}", it) }
            .getOrNull()
        return mergeExternalMetadata(title, external)
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

    private fun searchCacheKey(request: AnimeSearchRequest): String {
        val languageKey = when (appPreferences?.state?.value?.languageMode ?: LanguageMode.SYSTEM) {
            LanguageMode.UKRAINIAN -> "uk"
            LanguageMode.ENGLISH -> "en"
            LanguageMode.RUSSIAN -> "ru"
            LanguageMode.SYSTEM -> "sys"
        }
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

    private object RelatedAnimeTitleMapper {
        fun map(related: org.akkirrai.beakokit.model.RelatedAnimeTitle): RelatedAnime {
            return RelatedAnime(
                id = related.id,
                title = related.title,
                posterUrl = related.posterUrl,
                type = related.type,
                year = related.year,
                episodeCount = related.episodeCount,
                status = related.status,
            )
        }
    }

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
