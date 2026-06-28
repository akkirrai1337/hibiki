package org.akkirrai.hibiki.core.source

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.akkirrai.animeresolver.core.TitleMatcher
import org.akkirrai.animeresolver.metadata.YummyMetadataSource
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeSearchSort
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.core.account.AndroidKeystoreYummyApplicationTokenStore
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeRating
import org.akkirrai.hibiki.core.model.RelatedAnime
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.network.hasActiveInternetConnection
import java.util.concurrent.ConcurrentHashMap

class AnimeSearchRepository(
    context: Context? = null,
    private val client: HttpClient = AndroidHttpClientFactory.create(),
) {
    private val appContext = context?.applicationContext
    private val appPreferences = appContext?.let(::AppPreferences)
    private val applicationTokenStore = appContext?.let(::AndroidKeystoreYummyApplicationTokenStore)
    private val yummySource = YummyMetadataSource(
        client = client,
        applicationToken = applicationTokenStore?.getEffectiveApplicationToken(),
        debugLogger = { message -> AppLogger.d(TAG, message) },
        languageProvider = ::yummyLanguage,
    )
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
        val results = yummySource.search(normalizedRequest)
            .map { title -> title.toAnime(preferEnglish = preferEnglish) }

        searchCache[cacheKey] = CachedSearchResults(items = results)
        return results
    }

    suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog {
        return YummySearchFilterLocalizer.localize(
            catalog = yummySource.getSearchFilterCatalog(),
            preferEnglish = preferEnglish(),
        )
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

            val resolvedId = resolveYummyId(
                rawId = id,
                fallbackTitle = fallback.title,
            )
            val anime = yummySource.getById(resolvedId)
                .toAnime(
                    canonicalId = resolvedId,
                    preferEnglish = preferEnglish(),
                    fallback = fallback,
                )

            detailsCache[cacheKey] = CachedAnime(
                anime = anime,
            )
            anime
        }
    }

    fun close() {
        client.close()
    }

    private suspend fun resolveYummyId(
        rawId: String,
        fallbackTitle: String,
    ): String {
        val normalizedId = YummyIdMigration.normalizeTitleId(rawId)
        if (normalizedId.all(Char::isDigit)) {
            return normalizedId
        }

        val title = fallbackTitle.trim()
        if (title.isBlank()) {
            return normalizedId
        }

        return yummySource.search(title)
            .bestMatchFor(title)
            ?.id
            ?: normalizedId
    }

    private fun AnimeTitle.toAnime(
        canonicalId: String = id,
        preferEnglish: Boolean,
        fallback: Anime? = null,
    ): Anime {
        val posterUrl = posterUrl ?: fallback?.posterUrl
        val resolvedStatus = status?.takeIf(String::isNotBlank)
            ?: fallback?.status
            ?: if (preferEnglish) "Unknown" else "Неизвестно"
        return Anime(
            id = canonicalId,
            title = displayTitle(preferEnglish),
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
            sourceMaterial = sourceMaterial ?: fallback?.sourceMaterial,
            studios = studios.ifEmpty { fallback?.studios.orEmpty() },
            franchiseAnime = franchiseAnime.map(RelatedAnimeTitleMapper::map)
                .ifEmpty { fallback?.franchiseAnime.orEmpty() },
            relatedAnime = relatedAnime.map(RelatedAnimeTitleMapper::map)
                .ifEmpty { fallback?.relatedAnime.orEmpty() },
            releaseDate = formatReleaseDate(preferEnglish) ?: fallback?.releaseDate,
        )
    }

    private fun AnimeTitle.buildAlternativeTitles(
        preferEnglish: Boolean,
        fallbackTitles: List<String>,
    ): List<String> {
        val primaryTitle = displayTitle(preferEnglish)
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

    private fun AnimeTitle.displayTitle(preferEnglish: Boolean): String {
        return if (preferEnglish) {
            englishName?.takeIf(String::isNotBlank)
                ?: originalName.takeIf(String::isNotBlank)
                ?: russianName.orEmpty()
        } else {
            russianName?.takeIf(String::isNotBlank)
                ?: englishName?.takeIf(String::isNotBlank)
                ?: originalName
        }
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
        return when (val count = episodeCount) {
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

    private fun yummyLanguage(): String = if (preferEnglish()) "en" else "ru"

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
        return "$DETAILS_CACHE_VERSION:$languageKey:$id"
    }

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
        fun map(related: org.akkirrai.animeresolver.model.RelatedAnimeTitle): RelatedAnime {
            return RelatedAnime(
                id = related.id,
                title = related.title,
                posterUrl = related.posterUrl,
                type = related.type,
                year = related.year,
                episodeCount = related.episodeCount,
            )
        }
    }

    private companion object {
        const val TAG = "AnimeSearchRepository"
        const val SEARCH_CACHE_VERSION = 2
        const val SEARCH_PAGE_SIZE = 20
        const val DETAILS_CACHE_VERSION = 1
        const val LEGACY_ID_MATCH_CONFIDENCE = 0.72

        val searchCache = ConcurrentHashMap<String, CachedSearchResults>()
        val detailsCache = ConcurrentHashMap<String, CachedAnime>()
    }
}
