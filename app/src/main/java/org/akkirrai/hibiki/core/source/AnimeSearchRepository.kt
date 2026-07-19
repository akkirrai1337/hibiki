package org.akkirrai.hibiki.core.source

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
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
) {
    private val searchCache = ConcurrentHashMap<String, CachedSearchResults>()
    private val detailsCache = ConcurrentHashMap<String, CachedAnime>()
    private val appContext = context?.applicationContext
    private val appPreferences = appContext?.let(::AppPreferences)
    private val sourceManager = appContext?.let { AnimeSourceRuntimeManager(it, client) }
    private val titleMatcher = TitleMatcher()
    private val detailsMutex = Mutex()

    suspend fun search(query: String): List<Anime> {
        return search(query = query, limit = SEARCH_PAGE_SIZE, offset = 0)
    }

    suspend fun search(request: AnimeSearchRequest): List<Anime> {
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
        val cacheKey = searchCacheKey(normalizedRequest)
        getCachedSearch(cacheKey)?.let { return it }

        ensureInternetConnection()

        val preferEnglish = preferEnglish()
        val results = currentSource().search(normalizedRequest)
            .map { title -> title.toAnime(preferEnglish = preferEnglish) }

        searchCache[cacheKey] = CachedSearchResults(items = results)
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

    suspend fun getDetails(id: String, fallback: Anime): Anime {
        AppLogger.d(TAG, "getDetails(id=$id, fallback.title=${fallback.title.take(50)})")
        val cacheKey = detailsCacheKey(id)
        getCachedDetails(cacheKey)?.let {
            AppLogger.d(TAG, "getDetails: cache hit for $cacheKey")
            return it
        }

        return detailsMutex.withLock {
            getCachedDetails(cacheKey)?.let { return@withLock it }

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
        val resolvedStatus = releaseStatus.localizedDisplayName(preferEnglish)
            .takeUnless { releaseStatus == AnimeReleaseStatus.UNKNOWN }
            ?: fallback?.status
            ?: if (preferEnglish) "Unknown" else "Неизвестно"
        return Anime(
            id = canonicalId,
            title = localizedDisplayName(
                languageMode = appPreferences?.state?.value?.languageMode ?: LanguageMode.SYSTEM,
                systemLanguage = appContext?.resources?.configuration?.locales?.get(0)?.language,
            ),
            subtitle = buildSubtitle(fallback?.subtitle),
            episodesLabel = if (resolvedStatus.isAnnouncementStatus()) {
                if (preferEnglish) "announcement" else "анонс"
            } else {
                buildEpisodesLabel(fallback?.episodesLabel, preferEnglish)
            },
            status = resolvedStatus,
            nextEpisodeAt = nextEpisodeAt ?: fallback?.nextEpisodeAt,
            posterUrl = posterUrl,
            posterFallbackUrl = fallback?.posterFallbackUrl
                ?.takeIf { it.isNotBlank() && it != posterUrl },
            description = description ?: fallback?.description,
            genres = genres.ifEmpty { fallback?.genres.orEmpty() },
            alternativeTitles = buildAlternativeTitles(preferEnglish, fallback?.alternativeTitles.orEmpty()),
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

    private fun AnimeTitle.buildAlternativeTitles(
        preferEnglish: Boolean,
        fallbackTitles: List<String>,
    ): List<String> {
        val primaryTitle = localizedDisplayName(
            languageMode = appPreferences?.state?.value?.languageMode ?: LanguageMode.SYSTEM,
            systemLanguage = appContext?.resources?.configuration?.locales?.get(0)?.language,
        )
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
        val releasedCount = availableEpisodeCount
            ?: episodeCount.takeIf { releaseStatus == AnimeReleaseStatus.RELEASED }
        return when (val count = releasedCount) {
            null -> fallbackLabel.orEmpty().ifBlank {
                if (preferEnglish) "Episodes unknown" else "Количество серий неизвестно"
            }
            else -> if (preferEnglish) "$count episodes" else "$count серий"
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

    private fun preferEnglish(): Boolean {
        return when (appPreferences?.state?.value?.languageMode ?: LanguageMode.SYSTEM) {
            LanguageMode.ENGLISH -> true
            LanguageMode.RUSSIAN -> false
            LanguageMode.SYSTEM -> false
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
            LanguageMode.ENGLISH -> "en"
            LanguageMode.RUSSIAN -> "ru"
            LanguageMode.SYSTEM -> "sys"
        }
        val sourceId = sourceManager?.forTitle(id)?.descriptor?.id ?: selectedSourceId()
        return "$DETAILS_CACHE_VERSION:${sourceId.value}:$languageKey:$id"
    }

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
        const val TAG = "AnimeSearchRepository"
        const val SEARCH_CACHE_VERSION = 2
        const val SEARCH_PAGE_SIZE = 20
        const val DETAILS_CACHE_VERSION = 1
        const val LEGACY_ID_MATCH_CONFIDENCE = 0.72
    }
}
