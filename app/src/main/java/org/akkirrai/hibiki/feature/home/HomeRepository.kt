package org.akkirrai.hibiki.feature.home

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random
import org.akkirrai.animeresolver.metadata.YummyMetadataSource
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeSearchSort
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.network.bodyOrThrow
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeRating
import org.akkirrai.hibiki.core.account.AndroidKeystoreYummyApplicationTokenStore
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.MockAnimeData
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.network.hasActiveInternetConnection
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.localizedDisplayName
import org.akkirrai.hibiki.core.source.WatchStateRepository

class HomeRepository(
    context: Context,
    private val client: HttpClient = AndroidHttpClientFactory.create(),
) {
    @Volatile
    private var cachedHomeContent: CachedHomeContent? = null

    @Volatile
    private var cachedRecentUpdates: List<Anime>? = null

    @Volatile
    private var currentHomeSelectionSeed: Long? = null

    private val appContext = context.applicationContext
    private val appPreferences = AppPreferences(appContext)
    private val applicationTokenStore = AndroidKeystoreYummyApplicationTokenStore(appContext)
    private val yummySource = YummyMetadataSource(
        client = client,
        applicationToken = applicationTokenStore.getEffectiveApplicationToken(),
        debugLogger = { message -> AppLogger.d(TAG, message) },
        languageProvider = ::yummyLanguage,
    )
    private val searchRepository = AnimeSearchRepository(appContext, client)
    private val watchStateRepository = WatchStateRepository(appContext)

    fun fallbackHomeState(): HomeUiState {
        return HomeUiState(
            featuredAnime = MockAnimeData.trending.take(FEATURED_COUNT),
            continueAnime = null,
            popular = emptyList(),
            trending = MockAnimeData.trending,
            recentlyUpdated = MockAnimeData.recent,
        )
    }

    suspend fun refreshHomeState(): HomeUiState {
        AppLogger.d(TAG, "refreshHomeState: clearing cache")
        ensureInternetConnection()
        cachedHomeContent = null
        cachedRecentUpdates = null
        currentHomeSelectionSeed = Random.nextLong()
        AppLogger.d(TAG, "refreshHomeState: advanced home selection seed to $currentHomeSelectionSeed")
        return loadHomeState()
    }

    suspend fun loadHomeState(): HomeUiState {
        AppLogger.d(TAG, "loadHomeState: called")
        val selectionSeed = currentHomeSelectionSeed ?: Random.nextLong().also {
            currentHomeSelectionSeed = it
        }
        val languageKey = yummyLanguage()
        cachedHomeContent?.let { cached ->
            if (cached.selectionSeed == selectionSeed && cached.languageKey == languageKey) {
                AppLogger.d(TAG, "loadHomeState: using cachedHomeContent — " +
                    "trending=${cached.trending.size}, recentlyUpdated=${cached.recentlyUpdated.size}, seed=$selectionSeed, lang=$languageKey")
                return HomeUiState(
                    featuredAnime = cached.featuredAnime,
                    continueAnime = loadContinueAnime(),
                    popular = emptyList(),
                    trending = cached.trending,
                    recentlyUpdated = cached.recentlyUpdated,
                )
            }
        }

        ensureInternetConnection()

        val trendingOffset = trendingOffsetForSeed(selectionSeed)
        AppLogger.d(TAG, "loadHomeState: cache miss, calling getCatalog(limit=$HOME_TRENDING_WINDOW_SIZE, offset=$trendingOffset, lang=$languageKey)")
        val catalog = yummySource.getCatalog(
            limit = HOME_TRENDING_WINDOW_SIZE,
            offset = trendingOffset,
            sort = "top",
        )
        AppLogger.d(TAG, "loadHomeState: getCatalog returned ${catalog.size} items")

        if (catalog.isEmpty()) {
            AppLogger.w(TAG, "loadHomeState: catalog empty")
            throw IllegalStateException(appContext.getString(R.string.home_error_load_failed))
        }

        val homeWindow = catalog.map(::toHomeAnime)
        val featuredAnime = homeWindow
            .shuffled(Random(selectionSeed xor FEATURED_ROTATION_SEED_SALT))
            .take(FEATURED_COUNT)
        val featuredIds = featuredAnime.mapTo(mutableSetOf()) { it.id }
        val trending = homeWindow
            .shuffled(Random(selectionSeed xor TRENDING_ROTATION_SEED_SALT))
            .filterNot { it.id in featuredIds }
            .take(HOME_SECTION_LIMIT)
        AppLogger.d(TAG, "loadHomeState: calling loadRecentlyUpdated()")
        val recentlyUpdated = loadRecentlyUpdated()
        AppLogger.d(TAG, "loadHomeState: recentlyUpdated size = ${recentlyUpdated.size}")
        cachedHomeContent = CachedHomeContent(
            selectionSeed = selectionSeed,
            languageKey = languageKey,
            featuredAnime = featuredAnime,
            trending = trending,
            recentlyUpdated = recentlyUpdated,
        )
        AppLogger.d(TAG, "loadHomeState: cachedHomeContent written — " +
            "trending=${trending.size}, recentlyUpdated=${recentlyUpdated.size}")

        return HomeUiState(
            featuredAnime = featuredAnime,
            continueAnime = loadContinueAnime(),
            popular = emptyList(),
            trending = trending,
            recentlyUpdated = recentlyUpdated,
        )
    }

    suspend fun search(query: String): List<Anime> {
        AppLogger.d(TAG, "search(query=$query)")
        ensureInternetConnection()
        return searchRepository.search(query)
    }

    suspend fun search(
        query: String,
        filters: HomeSearchFilters,
        limit: Int,
        offset: Int,
    ): List<Anime> {
        AppLogger.d(TAG, "search(query=$query, filters=$filters, limit=$limit, offset=$offset)")
        ensureInternetConnection()
        return searchRepository.search(
            AnimeSearchRequest(
                query = query,
                limit = limit,
                offset = offset,
                sort = filters.sortAlias.toSearchSort(),
                typeAliases = listOfNotNull(filters.typeAlias),
                statusAliases = listOfNotNull(filters.statusAlias),
                includedGenreAliases = filters.includedGenreAliases.sorted(),
                excludedGenreAliases = filters.excludedGenreAliases.sorted(),
                yearFrom = filters.yearFrom,
                yearTo = filters.yearTo,
            )
        )
    }

    suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog {
        return searchRepository.getSearchFilterCatalog()
    }

    fun close() {
        searchRepository.close()
    }

    private suspend fun loadContinueAnime(): Anime? {
        return watchStateRepository.getRecentTitleWatchState()
            ?.let { progress ->
                runCatching {
                    searchRepository.getDetails(
                        id = progress.titleId,
                        fallback = Anime(
                            id = progress.titleId,
                            title = "",
                            subtitle = "",
                            episodesLabel = "",
                            status = "",
                        ),
                    )
                }.getOrNull()
            }
    }

    private fun ensureInternetConnection() {
        if (!hasActiveInternetConnection(appContext)) {
            throw NoInternetConnectionException(appContext.getString(R.string.home_error_no_internet))
        }
    }

    suspend fun loadRecentlyUpdatedPage(
        offset: Int,
        limit: Int = HOME_SECTION_LIMIT,
    ): List<Anime> {
        val catalog = cachedRecentUpdates ?: loadRecentlyUpdatedCatalog().also {
            cachedRecentUpdates = it
        }
        return catalog.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(1))
    }

    private suspend fun loadRecentlyUpdated(): List<Anime> =
        loadRecentlyUpdatedPage(offset = 0)

    private suspend fun loadRecentlyUpdatedCatalog(): List<Anime> {
        val applicationToken = applicationTokenStore.getEffectiveApplicationToken()
        AppLogger.d(
            TAG,
            "loadRecentlyUpdated: fetching GET /anime/schedule, xApplicationAttached=${!applicationToken.isNullOrBlank()}",
        )
        val response = runCatching {
            client.get("$YUMMY_BASE_URL/anime/schedule") {
                header("Lang", yummyLanguage())
                applicationToken?.takeIf(String::isNotBlank)?.let {
                    header("X-Application", it)
                }
            }.bodyOrThrow<YummyScheduleEnvelope>("YummyAnime").response
        }.onFailure { e ->
            AppLogger.e(TAG, "loadRecentlyUpdated: request failed — ${e::class.simpleName}: ${e.message}", e)
        }.getOrElse {
            AppLogger.w(TAG, "loadRecentlyUpdated: returning empty list after failure")
            return emptyList()
        }
        AppLogger.d(TAG, "loadRecentlyUpdated: raw response has ${response.size} items")

        val result = response
            .asSequence()
            .filter { anime ->
                val prev = anime.episodes?.prevDate ?: 0L
                val next = anime.episodes?.nextDate ?: 0L
                val aired = anime.episodes?.aired ?: 0
                val pass = prev > 0L || (aired <= 0 && next > 0L)
                if (!pass) {
                    val id = anime.animeId
                    AppLogger.v(TAG, "loadRecentlyUpdated: filtered out anime_id=$id, prevDate=$prev, nextDate=$next")
                }
                pass
            }
            .sortedByDescending { anime ->
                val prev = anime.episodes?.prevDate ?: 0L
                if (prev > 0L) prev else anime.episodes?.nextDate ?: 0L
            }
            .distinctBy(YummyScheduleAnime::animeId)
            .map(::toScheduledHomeAnime)
            .toList()
        AppLogger.d(TAG, "loadRecentlyUpdated: after filter/sort/distinct/take — ${result.size} items")
        return result
    }

    suspend fun loadTrendingPage(
        offset: Int,
        limit: Int = HOME_FULL_SECTION_LIMIT,
        filter: TrendingFilter = TrendingFilter.All,
    ): List<Anime> {
        AppLogger.d(TAG, "loadTrendingPage: offset=$offset, limit=$limit, filter=$filter")
        val catalog = yummySource.getCatalog(
            limit = limit,
            offset = offset,
            sort = "top",
            type = filter.yummyType,
        )
        AppLogger.d(TAG, "loadTrendingPage: got ${catalog.size} items from getCatalog")
        return catalog.map(::toHomeAnime)
    }

    suspend fun loadRandomAnime(excludedIds: Set<String>): Anime? {
        ensureInternetConnection()
        repeat(RANDOM_CATALOG_ATTEMPTS) {
            val catalog = yummySource.getCatalog(
                limit = RANDOM_CATALOG_PAGE_SIZE,
                offset = Random.nextInt(RANDOM_CATALOG_MAX_OFFSET),
                sort = RANDOM_CATALOG_SORTS.random(),
            )
            val candidates = catalog
                .map(::toHomeAnime)
                .filterNot { it.id in excludedIds }
            candidates.randomOrNull()?.let { return it }
        }
        return null
    }

    suspend fun enrichDescriptions(items: List<Anime>): List<Anime> {
        return items.map { anime ->
            runCatching { yummySource.getById(anime.id) }
                .getOrNull()
                ?.description
                ?.takeIf(String::isNotBlank)
                ?.let { description -> anime.copy(description = description) }
                ?: anime
        }
    }

    private fun toHomeAnime(title: AnimeTitle): Anime {
        val subtitle = buildList {
            title.type?.toDisplayType()?.let(::add)
            title.year?.toString()?.let(::add)
        }.joinToString(" · ")

        val status = title.status.orEmpty()
        val isAnnouncement = status.isAnnouncementStatus()
        return Anime(
            id = title.id,
            title = displayTitle(title),
            subtitle = subtitle,
            episodesLabel = if (isAnnouncement) announcementLabel() else title.episodeCount?.let { episodesCountLabel(it) }.orEmpty(),
            status = status,
            nextEpisodeAt = title.nextEpisodeAt,
            posterUrl = title.posterUrl,
            description = title.description,
            ratings = title.ratings.map { rating ->
                AnimeRating(source = rating.source, value = rating.value, votes = rating.votes)
            },
            genres = title.genres,
            studios = title.studios,
        )
    }

    private fun String?.isAnnouncementStatus(): Boolean {
        val normalized = orEmpty().trim().lowercase()
        return normalized == "анонс" || normalized == "announcement" || normalized == "announced" || normalized == "anons"
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

    private fun String.toSearchSort(): AnimeSearchSort {
        return when (this) {
            "top" -> AnimeSearchSort.RATING
            "title" -> AnimeSearchSort.TITLE
            "year" -> AnimeSearchSort.YEAR
            "votes" -> AnimeSearchSort.VOTES
            "views" -> AnimeSearchSort.VIEWS
            "comments" -> AnimeSearchSort.COMMENTS
            else -> AnimeSearchSort.RELEVANCE
        }
    }

    private fun preferEnglish(): Boolean {
        return when (appPreferences.state.value.languageMode) {
            LanguageMode.ENGLISH -> true
            LanguageMode.RUSSIAN -> false
            LanguageMode.SYSTEM -> appContext.resources.configuration.locales[0]?.language != "ru"
        }
    }

    private fun yummyLanguage(): String = if (preferEnglish()) "en" else "ru"

    private fun displayTitle(title: AnimeTitle): String {
        return title.localizedDisplayName(
            languageMode = appPreferences.state.value.languageMode,
            systemLanguage = appContext.resources.configuration.locales[0]?.language,
        )
    }

    private fun isRussianLocale(): Boolean = !preferEnglish()

    private fun announcementLabel(): String = if (isRussianLocale()) "анонс" else "announcement"

    private fun episodesCountLabel(count: Int): String {
        return if (isRussianLocale()) {
            "$count серий"
        } else {
            "$count episodes"
        }
    }

    private fun releasedEpisodesLabel(aired: Int, total: Int?): String {
        val isRu = isRussianLocale()
        return when {
            total != null -> if (isRu) "$aired из $total серий" else "$aired of $total episodes"
            else -> if (isRu) "$aired серий вышло" else "$aired episodes released"
        }
    }

    private fun ongoingStatusLabel(): String {
        return if (isRussianLocale()) "Онгоинг" else "Ongoing"
    }

    private fun toScheduledHomeAnime(scheduleAnime: YummyScheduleAnime): Anime {
        val aired = scheduleAnime.episodes?.aired?.takeIf { it > 0 }
        val count = scheduleAnime.episodes?.count?.takeIf { it > 0 }
        val nextDate = scheduleAnime.episodes?.nextDate?.takeIf { it > 0L }
        val isAnnouncement = aired == null && nextDate != null
        return Anime(
            id = scheduleAnime.animeId.toString(),
            title = scheduleAnime.title?.takeIf(String::isNotBlank) ?: scheduleAnime.animeId.toString(),
            subtitle = "TV",
            episodesLabel = when {
                isAnnouncement -> announcementLabel()
                aired != null -> releasedEpisodesLabel(aired, count)
                count != null -> episodesCountLabel(count)
                else -> ""
            },
            status = if (isAnnouncement) announcementLabel() else ongoingStatusLabel(),
            nextEpisodeAt = nextDate,
            posterUrl = scheduleAnime.poster?.bestUrl(),
        )
    }

    private fun trendingOffsetForSeed(selectionSeed: Long): Int {
        return Random(selectionSeed).nextInt(
            from = 0,
            until = HOME_TRENDING_MAX_OFFSET_EXCLUSIVE,
        )
    }

    private companion object {
        const val TAG = "HomeRepository"
        const val YUMMY_BASE_URL = "https://api.yani.tv"
        const val HOME_SECTION_LIMIT = 12
        const val HOME_FULL_SECTION_LIMIT = 100
        const val HOME_TRENDING_WINDOW_SIZE = 24
        const val HOME_TRENDING_MAX_OFFSET_EXCLUSIVE = 201
        const val FEATURED_COUNT = 5
        const val FEATURED_ROTATION_SEED_SALT = 0x51A7L
        const val TRENDING_ROTATION_SEED_SALT = 0x7E4DL
        const val RANDOM_CATALOG_PAGE_SIZE = 40
        const val RANDOM_CATALOG_MAX_OFFSET = 5_000
        const val RANDOM_CATALOG_ATTEMPTS = 5
        val RANDOM_CATALOG_SORTS = listOf("top", "views", "votes", "year", "title", "comments")
    }

    private data class CachedHomeContent(
        val selectionSeed: Long,
        val languageKey: String,
        val featuredAnime: List<Anime>,
        val trending: List<Anime>,
        val recentlyUpdated: List<Anime>,
    )
}

@Serializable
private data class YummyScheduleEnvelope(
    val response: List<YummyScheduleAnime> = emptyList(),
)

@Serializable
private data class YummyScheduleAnime(
    @SerialName("anime_id") val animeId: Long,
    val title: String? = null,
    val poster: YummySchedulePoster? = null,
    val episodes: YummyScheduleEpisodes? = null,
) {
}

@Serializable
private data class YummyScheduleEpisodes(
    val aired: Int? = null,
    val count: Int? = null,
    @SerialName("prev_date") val prevDate: Long? = null,
    @SerialName("next_date") val nextDate: Long? = null,
)

@Serializable
private data class YummySchedulePoster(
    val fullsize: String? = null,
    val big: String? = null,
    val medium: String? = null,
    val small: String? = null,
    val huge: String? = null,
    val mega: String? = null,
) {
    fun bestUrl(): String? = listOf(mega, huge, big, medium, small, fullsize)
        .firstOrNull { !it.isNullOrBlank() }
        ?.let { if (it.startsWith("//")) "https:$it" else it }
}
