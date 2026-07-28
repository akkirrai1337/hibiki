package org.akkirrai.hibiki.core.source

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import org.akkirrai.beakokit.matching.TitleMatcher
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchFilter
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
import org.akkirrai.hibiki.shared.details.isAnnouncementStatus
import org.akkirrai.hibiki.shared.source.resolveEpisodesLabel
import org.akkirrai.hibiki.shared.source.formatReleaseDateLabel
import org.akkirrai.hibiki.shared.home.resolveDisplayTypeLabel
import org.akkirrai.hibiki.shared.source.resolveAlternativeTitles
import java.util.concurrent.ConcurrentHashMap

class AnimeSearchRepository(
    context: Context? = null,
    private val client: HttpClient = AndroidHttpClientFactory.create(),
) {
    private val searchCache = ConcurrentHashMap<String, CachedSearchResults>()
    private val detailsCache = ConcurrentHashMap<String, CachedAnime>()
    private val appContext = context?.applicationContext
    private val appPreferences = appContext?.let(::AppPreferences)
    private val sourceManager = appContext?.let { AnimeSourceRuntimeManager(it, client) }
    private val titleMatcher = TitleMatcher()
    private val detailsMutexes = ConcurrentHashMap<String, Mutex>()
    private val detailsRequestSlots = Semaphore(MAX_CONCURRENT_DETAILS_REQUESTS)

    suspend fun search(query: String): List<Anime> {
        return search(query = query, limit = SEARCH_PAGE_SIZE, offset = 0)
    }

    suspend fun search(request: AnimeSearchRequest): List<Anime> {
        return search(selectedSourceId(), request)
    }

    suspend fun search(sourceId: org.akkirrai.beakokit.api.SourceId, request: AnimeSearchRequest): List<Anime> {
        val normalizedQuery = request.query.trim()
        val hasFilters = request.typeAliases.isNotEmpty() ||
            request.statusAliases.isNotEmpty() ||
            request.includedGenreAliases.isNotEmpty() ||
            request.excludedGenreAliases.isNotEmpty() ||
            request.yearFrom != null ||
            request.yearTo != null ||
            request.sort != AnimeSearchSort.RELEVANCE
        if (normalizedQuery.isBlank() && !hasFilters) return emptyList()

        val normalizedRequest = request.copy(query = normalizedQuery)
        val cacheKey = searchCacheKey(sourceId, normalizedRequest)
        getCachedSearch(cacheKey)?.let { return it }

        ensureInternetConnection()

        val preferEnglish = preferEnglish()
        val source = sourceManager?.runtime(sourceId)
            ?: error("Anime source selection requires an Android context")
        val genreAliases = if (
            normalizedRequest.includedGenreAliases.isNotEmpty() ||
            normalizedRequest.excludedGenreAliases.isNotEmpty()
        ) {
            source.filterCatalog(preferEnglish).genreOptions.associate {
                normalizeFilterValue(it.title) to normalizeFilterValue(it.id)
            }
        } else {
            emptyMap()
        }
        val capabilities = source.source.catalogCapabilities
        val requiresMetadataFilters =
            (normalizedRequest.typeAliases.isNotEmpty() && !capabilities.supports(AnimeSearchFilter.TYPE)) ||
                (normalizedRequest.statusAliases.isNotEmpty() && !capabilities.supports(AnimeSearchFilter.STATUS)) ||
                (normalizedRequest.includedGenreAliases.isNotEmpty() && !capabilities.supports(AnimeSearchFilter.INCLUDED_GENRES)) ||
                (normalizedRequest.excludedGenreAliases.isNotEmpty() && !capabilities.supports(AnimeSearchFilter.EXCLUDED_GENRES)) ||
                ((normalizedRequest.yearFrom != null || normalizedRequest.yearTo != null) &&
                    !capabilities.supports(AnimeSearchFilter.YEAR_RANGE))
        val results = source.search(normalizedRequest)
            .mapNotNull { title ->
                if (!requiresMetadataFilters) {
                    title
                } else {
                    runCatching { source.details(title.id) }.getOrNull()?.let { details ->
                        title.mergeFilterMetadata(details)
                    }
                }
            }
            .filter { it.matchesSearchRequest(normalizedRequest, genreAliases) }
            .map { title ->
                getCachedDetails(detailsCacheKey(title.id))
                    ?: title.toAnime(preferEnglish = preferEnglish)
            }

        searchCache[cacheKey] = CachedSearchResults(items = results)
        return results
    }

    suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog {
        return currentSource().filterCatalog(preferEnglish())
    }

    suspend fun getSearchFilterCatalog(sourceId: org.akkirrai.beakokit.api.SourceId): AnimeSearchFilterCatalog {
        return sourceManager?.runtime(sourceId)?.filterCatalog(preferEnglish())
            ?: error("Anime source selection requires an Android context")
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

    suspend fun getDetails(id: String, fallback: Anime): Anime {
        AppLogger.d(TAG, "getDetails(id=$id, fallback.title=${fallback.title.take(50)})")
        val cacheKey = detailsCacheKey(id)
        getCachedDetails(cacheKey)?.let {
            AppLogger.d(TAG, "getDetails: cache hit for $cacheKey")
            return it
        }

        return detailsMutexes.computeIfAbsent(cacheKey) { Mutex() }.withLock {
            getCachedDetails(cacheKey)?.let { return@withLock it }

            detailsRequestSlots.withPermit {
                getCachedDetails(cacheKey)?.let { return@withPermit it }

                ensureInternetConnection()

                val source = sourceManager?.forTitle(id) ?: currentSource()
                val title = runCatching { source.details(id) }
                    .getOrElse {
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
                )
                anime
            }
        }
    }

    fun clearCaches() {
        searchCache.clear()
        detailsCache.clear()
    }

    fun close() {
        clearCaches()
        client.close()
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
            episodesLabel = if (isAnnouncementStatus(resolvedStatus)) {
                if (preferEnglish) "announcement" else "анонс"
            } else {
                resolveEpisodesLabel(
                    releasedCount = availableEpisodeCount
                        ?: episodeCount.takeIf { releaseStatus == AnimeReleaseStatus.RELEASED },
                    fallbackLabel = fallback?.episodesLabel,
                    preferEnglish = preferEnglish,
                )
            },
            status = resolvedStatus,
            nextEpisodeAt = nextEpisodeAt ?: fallback?.nextEpisodeAt,
            posterUrl = posterUrl,
            posterFallbackUrl = sourcePosterFallbackUrl ?: fallback?.posterFallbackUrl
                ?.takeIf { it.isNotBlank() && it != posterUrl },
            description = description ?: fallback?.description,
            genres = genres.ifEmpty { fallback?.genres.orEmpty() },
            alternativeTitles = resolveAlternativeTitles(
                primaryTitle = displayName,
                titleCandidates = listOf(russianName, englishName, originalName, japaneseName) + synonyms,
                fallbackTitles = fallback?.alternativeTitles.orEmpty(),
            ),
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
            releaseDate = formatReleaseDateLabel(year, season, preferEnglish) ?: fallback?.releaseDate,
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

    private fun AnimeTitle.buildSubtitle(fallbackSubtitle: String?): String {
        val parts = listOfNotNull(
            type?.let(::resolveDisplayTypeLabel),
            year?.toString(),
        )
        return parts.joinToString(" · ").ifBlank { fallbackSubtitle.orEmpty() }
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

    private fun preferEnglish(): Boolean {
        return when (appPreferences?.state?.value?.languageMode ?: LanguageMode.SYSTEM) {
            LanguageMode.ENGLISH -> true
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

    private fun searchCacheKey(sourceId: org.akkirrai.beakokit.api.SourceId, request: AnimeSearchRequest): String {
        val languageKey = if (preferEnglish()) "en" else "ru"
        val types = request.typeAliases.sorted().joinToString(",")
        val statuses = request.statusAliases.sorted().joinToString(",")
        val includedGenres = request.includedGenreAliases.sorted().joinToString(",")
        val excludedGenres = request.excludedGenreAliases.sorted().joinToString(",")
        return buildString {
            append(SEARCH_CACHE_VERSION)
            append(':')
            append(sourceId.value)
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
        val languageKey = if (preferEnglish()) "en" else "ru"
        val sourceId = sourceManager?.forTitle(id)?.descriptor?.id ?: selectedSourceId()
        return "$DETAILS_CACHE_VERSION:${sourceId.value}:$languageKey:$id"
    }

    private fun AnimeTitle.matchesSearchRequest(
        request: AnimeSearchRequest,
        genreAliases: Map<String, String>,
    ): Boolean {
        val titleYear = year
        val yearFrom = request.yearFrom
        val yearTo = request.yearTo
        val yearMatches = (yearFrom == null && yearTo == null) ||
            (titleYear != null &&
                (yearFrom == null || titleYear >= yearFrom) &&
                (yearTo == null || titleYear <= yearTo))
        val requestedTypes = request.typeAliases.map(::normalizeFilterValue).filter(String::isNotBlank)
        val typeMatches = requestedTypes.isEmpty() || normalizeFilterValue(type).let(requestedTypes::contains)
        val requestedStatuses = request.statusAliases.map(::normalizeFilterValue).filter(String::isNotBlank)
        val actualStatuses = listOfNotNull(status?.let(::normalizeFilterValue), releaseStatus.name.lowercase())
        val statusMatches = requestedStatuses.isEmpty() || actualStatuses.any(requestedStatuses::contains)
        val canonicalGenres = genres.map { genre ->
            normalizeFilterValue(genre).let { genreAliases[it] ?: it }
        }.toSet()
        val includedGenres = request.includedGenreAliases.map(::normalizeFilterValue)
        val excludedGenres = request.excludedGenreAliases.map {
            normalizeFilterValue(it.removePrefix("!"))
        }
        val genresMatch = (
            (includedGenres.isEmpty() || (canonicalGenres.isNotEmpty() && includedGenres.any(canonicalGenres::contains))) &&
                excludedGenres.none { it == UNSUPPORTED_FILTER_ALIAS || canonicalGenres.contains(it) }
            )
        return yearMatches && typeMatches && statusMatches && genresMatch
    }

    private fun AnimeTitle.mergeFilterMetadata(details: AnimeTitle): AnimeTitle = copy(
        type = details.type ?: type,
        status = details.status ?: status,
        year = details.year ?: year,
        genres = details.genres.ifEmpty { genres },
    )

    private fun normalizeFilterValue(value: String?): String = value.orEmpty()
        .trim()
        .lowercase()
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")

    private fun selectedSourceId() = sourceManager?.selectedId
        ?: error("Anime source selection requires an Android context")

    private fun currentSource(): AnimeSourceRuntime = sourceManager?.current()
        ?: error("Anime source selection requires an Android context")

    private fun getCachedSearch(key: String): List<Anime>? {
        val cached = searchCache[key] ?: return null
        return cached.items
    }

    private fun getCachedDetails(key: String): Anime? {
        val cached = detailsCache[key] ?: return null
        return cached.anime
    }

    private data class CachedSearchResults(
        val items: List<Anime>,
    )

    private data class CachedAnime(
        val anime: Anime,
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
        const val UNSUPPORTED_FILTER_ALIAS = "__hibiki_unsupported_filter__"
        const val TAG = "AnimeSearchRepository"
        const val SEARCH_CACHE_VERSION = 3
        const val SEARCH_PAGE_SIZE = 20
        const val MAX_CONCURRENT_DETAILS_REQUESTS = 3
        const val DETAILS_CACHE_VERSION = 1
        const val LEGACY_ID_MATCH_CONFIDENCE = 0.72
    }
}
