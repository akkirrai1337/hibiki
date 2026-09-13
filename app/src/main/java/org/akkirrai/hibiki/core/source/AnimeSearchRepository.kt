package org.akkirrai.hibiki.core.source

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.akkirrai.beakokit.metadata.ExternalMetadata
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
import org.akkirrai.beakokit.model.RelatedAnimeTitle
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
    private val filterCatalogCache = ConcurrentHashMap<String, AnimeSearchFilterCatalog>()
    private val pendingCardMatches = ConcurrentHashMap.newKeySet<String>()
    private val appContext = context?.applicationContext
    private val appPreferences = appContext?.let(::AppPreferences)
    private val sourceManager = sourceManager ?: appContext?.let { AnimeSourceRuntimeManager(it, client) }
    private val titleMatcher = TitleMatcher()
    // Built here rather than injected: everything it needs (the shared client, the app's own
    // preferences) is already on this repository, and nothing else in the app describes a title.
    private val metadataService = metadataService
        ?: appContext?.let {
            ExternalMetadataService(
                client,
                PreferencesExternalMetadataStore(it),
                log = { message -> AppLogger.d("ExternalMetadata", message) },
            )
        }
    // Owns preference observation and is cancelled with the repository.
    private val metadataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _cardMetadata = MutableStateFlow<Map<String, Anime>>(emptyMap())
    /** Durable overlay for cards that completed an aggregator match. State, not an event, so a
     * screen opening or paginating after a result cannot miss it. */
    val cardMetadata = _cardMetadata.asStateFlow()
    private val _pendingCardMetadata = MutableStateFlow<Set<String>>(emptySet())
    /** Source cards whose aggregator lookup is still in progress. */
    val pendingCardMetadata = _pendingCardMetadata.asStateFlow()
    private val detailsRequestSlots = Semaphore(MAX_CONCURRENT_DETAILS_REQUESTS)

    init {
        // Changing whether/which/how external metadata is fetched can only be seen by the user
        // once already-cached (possibly stale, differently merged) lists and details are dropped.
        // The initial value is skipped: the cache is already empty right after construction.
        appPreferences?.state
            ?.map { RelevantMetadataSettings(it) }
            ?.distinctUntilChanged()
            ?.drop(1)
            ?.onEach { clearCaches() }
            ?.launchIn(metadataScope)
    }

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
        val results = describeAll(source, sourceTitles)
            .map { title ->
                val anime = getCachedDetails(detailsCacheKey(title.id))
                    ?: title.toAnime(preferEnglish = preferEnglish)
                // The cached entry may be the details page's fully merged Anime, whose title comes
                // from the aggregator. This list only ever shows the source's own title (see
                // describeAll's doc), so that field is re-applied here even on a cache hit.
                (cardMetadata.value[title.id] ?: anime).copy(title = title.displayName)
            }

        searchCache[cacheKey] = CachedSearchResults(
            items = results,
            cachedAt = System.currentTimeMillis(),
        )
        trimOldestEntries(searchCache, MAX_SEARCH_CACHE_ENTRIES) { it.cachedAt }
        return results
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
            )
        )
    }

    /**
     * The source's own "latest releases" list, described exactly like a search page - same
     * describe-then-convert path, same cache.
     *
     * Home used to build these cards straight from the source runtime's [AnimeTitle]s, without
     * running them through [describeAll] at all, so fields like year and rating could be missing or
     * stale compared to the title page. Routing it through this one path fixes that - but the name
     * stays deliberately different: this row keeps the source's own title, while its details screen
     * shows the aggregator's, exactly as [describeAll]'s doc explains.
     */
    suspend fun latest(limit: Int, forceRefresh: Boolean = false): List<Anime> {
        val cacheKey = "latest:${selectedSourceId().value}:$limit:${languageKey()}"
        if (!forceRefresh) getCachedSearch(cacheKey)?.let { return it }

        ensureInternetConnection()

        val preferEnglish = preferEnglish()
        val source = currentSource()
        val results = describeAll(source, source.latest(limit)).map { title ->
            val anime = getCachedDetails(detailsCacheKey(title.id)) ?: title.toAnime(preferEnglish = preferEnglish)
            // See search(): a details-cache hit carries the aggregator-merged title, but this list
            // always shows the source's own title, so it is re-applied here too.
            (cardMetadata.value[title.id] ?: anime).copy(title = title.displayName)
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
        /** Skips a cached copy - for a title just opened from an aggregator card, which may now be
         * described by a different provider than the copy that was cached. */
        bypassCache: Boolean = false,
    ): Anime {
        AppLogger.d(TAG, "getDetails(id=$id, fallback.title=${fallback.title.take(50)})")
        val cacheKey = detailsCacheKey(id)
        if (!bypassCache) getCachedDetails(cacheKey)?.let {
            AppLogger.d(TAG, "getDetails: cache hit for $cacheKey")
            return it
        }

        val detailsMutex = detailsMutexes.computeIfAbsent(cacheKey) { Mutex() }
        return try {
            detailsMutex.withLock {
                if (!bypassCache) getCachedDetails(cacheKey)?.let { return@withLock it }

                detailsRequestSlots.withPermit {
                    if (!bypassCache) getCachedDetails(cacheKey)?.let { return@withPermit it }

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
                    val enrichedSections = describeRelatedAnime(
                        source,
                        listOf(described.relatedAnime, described.franchiseAnime, described.similarAnime),
                    )
                    val describedFull = described.copy(
                        relatedAnime = enrichedSections[0],
                        franchiseAnime = enrichedSections[1],
                        similarAnime = enrichedSections[2],
                    )
                    val trailer = describedFull.trailer?.toAnimeTrailer()
                    val anime = describedFull.toAnime(
                        canonicalId = describedFull.id,
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
        pendingCardMatches.clear()
        _cardMetadata.value = emptyMap()
        _pendingCardMetadata.value = emptySet()
    }

    fun close() {
        searchCache.clear()
        filterCatalogCache.clear()
        pendingCardMatches.clear()
        _cardMetadata.value = emptyMap()
        _pendingCardMetadata.value = emptySet()
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

    /**
     * Describes a whole list, card by card.
     *
     * The name on a list card is deliberately kept as the source's own, never the aggregator's,
     * even once a match has been found - this list, unlike the title page, is not enriched with the
     * provider's title. The details page follows the same rule: the source owns the ID that opens
     * for playback, while the aggregator enriches only the descriptive fields around that title.
     *
     * A list never asks the source for full details before a card is opened. Cached aggregator
     * data is applied synchronously; new matches continue in the background and update the durable
     * [cardMetadata] overlay. This keeps the source list responsive without losing an update when
     * the UI is loading, paginating, or temporarily off screen.
     */
    private suspend fun describeAll(source: AnimeSourceRuntime, titles: List<AnimeTitle>): List<AnimeTitle> {
        val service = metadataService ?: return titles
        val order = providerOrderFor(source)
        if (order.isEmpty()) {
            if (titles.isNotEmpty()) {
                AppLogger.d(
                    TAG,
                    "describeAll: source=${source.descriptor.id.value} skipped, provider order is empty " +
                        "(useExternalMetadata=${source.descriptor.info.useExternalMetadata}, " +
                        "userEnabled=${appPreferences?.state?.value?.externalMetadataEnabled})",
                )
            }
            return titles
        }
        if (titles.isEmpty()) return titles

        val cached = titles.map { service.cachedMetadataFor(it.id, order) }
        AppLogger.d(
            TAG,
            "describeAll: source=${source.descriptor.id.value} order=$order titles=${titles.size} " +
                "alreadyDescribed=${cached.count { it != null }} deferredUntilDetails=${cached.count { it == null }}",
        )
        titles.filterIndexed { index, title -> cached[index] == null && title.id !in cardMetadata.value }
            .takeIf(List<AnimeTitle>::isNotEmpty)
            ?.let { warmCardMetadata(service, order, it) }
        return titles.mapIndexed { index, title ->
            val external = cached[index]
            // mergeExternalMetadata keeps source-owned names. Re-applying these two fields also
            // protects this list from details cached by an older app version.
            val described = mergeExternalMetadata(title, external).copy(
                englishName = title.englishName,
                originalName = title.originalName,
            )
            described
        }
    }

    /** Matches a source card without calling source.details(). The completed card is retained in
     * [cardMetadata], not sent as a one-shot event. */
    private fun warmCardMetadata(
        service: ExternalMetadataService,
        order: List<MetadataProviderId>,
        titles: List<AnimeTitle>,
    ) {
        // Claim IDs before launching so duplicate pages/searches cannot create a second lookup
        // or leave a permanent loading indicator behind.
        val titlesToLoad = titles.filter { title ->
            title.id !in cardMetadata.value && pendingCardMatches.add(title.id)
        }
        if (titlesToLoad.isEmpty()) return
        _pendingCardMetadata.update { pending -> pending + titlesToLoad.map(AnimeTitle::id) }
        metadataScope.launch {
            for (title in titlesToLoad) {
                try {
                    runCatching { service.metadataFor(title, order) }
                        .onSuccess { external ->
                            external ?: return@onSuccess
                            val enriched = mergeExternalMetadata(title, external)
                                .toAnime(preferEnglish = preferEnglish())
                            _cardMetadata.update { known -> known + (title.id to enriched) }
                        }
                        .onFailure { AppLogger.w(TAG, "warmCardMetadata: ${title.id} not described", it) }
                } finally {
                    pendingCardMatches.remove(title.id)
                    _pendingCardMetadata.update { pending -> pending - title.id }
                }
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

    /**
     * Replaces a title's descriptive fields with a metadata provider's, when both the source asked
     * for that in its manifest and the user has not turned it off. Names and playback identity stay
     * with the source; the aggregator owns the presentation metadata around them.
     *
     * Failures are swallowed on purpose: a provider being unreachable, rate-limiting us, or simply
     * not carrying this title must cost the better description and nothing else - the source's own
     * screen still renders exactly as it did before this existed.
     */
    private suspend fun describe(source: AnimeSourceRuntime, title: AnimeTitle): AnimeTitle {
        val service = metadataService ?: return title
        val order = providerOrderFor(source)
        if (order.isEmpty()) return title
        val external = runCatching { service.metadataFor(title, order) }
            .onFailure { AppLogger.w(TAG, "describe: metadata lookup failed for ${title.id}", it) }
            .getOrNull()
        return mergeExternalMetadata(title, external)
    }

    /**
     * The same aggregator-description [describe] gives the title itself, extended to its
     * related/franchise/similar strips. Their source IDs remain actionable, so their source titles
     * stay intact while the aggregator contributes only descriptive fields.
     *
     * `sections` is franchise/related/similar together (not three separate calls) so a title that
     * happens to appear in more than one of them - which does happen, e.g. the current title itself
     * spliced into "related" - is only matched once. Requests stay sequential because this is a
     * handful of cards, not a scrollable list.
     */
    private suspend fun describeRelatedAnime(
        source: AnimeSourceRuntime,
        sections: List<List<RelatedAnimeTitle>>,
    ): List<List<RelatedAnimeTitle>> {
        val service = metadataService ?: return sections
        val order = providerOrderFor(source)
        val all = sections.flatten().distinctBy(RelatedAnimeTitle::id)
        if (order.isEmpty() || all.isEmpty()) return sections

        val describedById = all.associate { item ->
            val stub = AnimeTitle(id = item.id, originalName = item.title, englishName = item.title, posterUrl = item.posterUrl, year = item.year, type = item.type, status = item.status, availableEpisodeCount = item.episodeCount)
            val external = runCatching { service.metadataFor(stub, order) }
                .onFailure { AppLogger.w(TAG, "describeRelatedAnime: metadata lookup failed for ${item.id}", it) }
                .getOrNull()
            item.id to (external?.let { item.describedWith(it) } ?: item)
        }
        return sections.map { section -> section.map { describedById[it.id] ?: it } }
    }

    private fun RelatedAnimeTitle.describedWith(external: ExternalMetadata): RelatedAnimeTitle = copy(
        posterUrl = external.posterUrl ?: posterUrl,
        year = external.year ?: year,
        type = external.type ?: type,
        episodeCount = external.episodeCount ?: episodeCount,
        status = external.status ?: status,
    )

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
        return cached.items.map { anime -> cardMetadata.value[anime.id] ?: anime }
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

    /** The subset of [org.akkirrai.hibiki.app.settings.AppPreferencesState] that affects how
     * external metadata is fetched and merged - a change to any of these invalidates the caches
     * above, since they may now hold results produced under the old settings. */
    private data class RelevantMetadataSettings(
        val enabled: Boolean,
        val provider: MetadataProviderId,
        val fallback: Boolean,
        val overrides: Map<String, Boolean>,
    ) {
        constructor(state: org.akkirrai.hibiki.app.settings.AppPreferencesState) : this(
            enabled = state.externalMetadataEnabled,
            provider = state.externalMetadataProvider,
            fallback = state.externalMetadataFallback,
            overrides = state.externalMetadataOverrides,
        )
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
