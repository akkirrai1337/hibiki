package org.akkirrai.hibiki.feature.home

import android.content.Context
import io.ktor.client.HttpClient
import kotlin.random.Random
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeSearchSort
import org.akkirrai.animeresolver.model.AnimeReleaseStatus
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.model.AnimeRating
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.MockAnimeData
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.network.hasActiveInternetConnection
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.AnimeSourceRuntime
import org.akkirrai.hibiki.core.source.AnimeSourceRuntimeManager
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.localizedDisplayName
import org.akkirrai.hibiki.core.source.WatchStateRepository

class HomeRepository(
    context: Context,
    private val client: HttpClient = AndroidHttpClientFactory.create(),
) {
    @Volatile
    private var cachedHomeContent: CachedHomeContent? = null

    @Volatile
    private var cachedRecentUpdates: CachedSourceAnime? = null

    @Volatile
    private var currentHomeSelectionSeed: Long? = null

    private val appContext = context.applicationContext
    private val appPreferences = AppPreferences(appContext)
    private val sourceManager = AnimeSourceRuntimeManager(appContext, client)
    private val searchRepository = AnimeSearchRepository(appContext, client)
    private val watchStateRepository = WatchStateRepository(appContext)
    private val offlineTitleMetadataRepository = OfflineTitleMetadataRepository(appContext)
    private val libraryRepository = LibraryRepository(appContext)

    fun fallbackHomeState(): HomeUiState {
        return HomeUiState(
            featuredAnime = MockAnimeData.trending.take(FEATURED_COUNT),
            continueAnime = loadStoredContinueAnime(),
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
        val languageKey = "${selectedSourceId().value}:${sourceLanguage()}"
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
        val catalog = currentSource().search(
            AnimeSearchRequest(
                limit = HOME_TRENDING_WINDOW_SIZE,
                offset = trendingOffset,
                sort = AnimeSearchSort.RATING,
            ),
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
        val recentlyUpdated = runCatching { loadRecentlyUpdated() }
            .onFailure { error ->
                AppLogger.w(
                    TAG,
                    "loadHomeState: recent updates are unavailable: ${error.message}",
                )
            }
            .getOrDefault(emptyList())
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
        filters: AnimeSearchFilters,
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
        val progress = watchStateRepository.getRecentTitleWatchState() ?: return null
        val storedAnime = findStoredAnime(progress.titleId)
        val fallback = storedAnime ?: Anime(
            id = progress.titleId,
            title = "",
            subtitle = "",
            episodesLabel = "",
            status = "",
        )
        return runCatching {
            searchRepository.getDetails(id = progress.titleId, fallback = fallback)
                .also(offlineTitleMetadataRepository::save)
        }.getOrElse { error ->
            AppLogger.w(TAG, "Continue title ${progress.titleId} is unavailable: ${error.message}")
            storedAnime
        }
    }

    private fun loadStoredContinueAnime(): Anime? {
        val progress = watchStateRepository.getRecentTitleWatchState() ?: return null
        return findStoredAnime(progress.titleId)
    }

    private fun findStoredAnime(titleId: String): Anime? =
        offlineTitleMetadataRepository.get(titleId)
            ?: libraryRepository.getLibraryEntries()
                .firstOrNull { it.anime.id == titleId }
                ?.anime

    private fun ensureInternetConnection() {
        if (!hasActiveInternetConnection(appContext)) {
            throw NoInternetConnectionException(appContext.getString(R.string.home_error_no_internet))
        }
    }

    suspend fun loadRecentlyUpdatedPage(
        offset: Int,
        limit: Int = HOME_SECTION_LIMIT,
    ): List<Anime> {
        val sourceId = selectedSourceId()
        val catalog = cachedRecentUpdates
            ?.takeIf { it.sourceId == sourceId }
            ?.items
            ?: loadRecentlyUpdatedCatalog().also {
                cachedRecentUpdates = CachedSourceAnime(sourceId, it)
            }
        return catalog.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(1))
    }

    private suspend fun loadRecentlyUpdated(): List<Anime> =
        loadRecentlyUpdatedPage(offset = 0)

    private suspend fun loadRecentlyUpdatedCatalog(): List<Anime> {
        return currentSource()
            .latest(limit = HOME_FULL_SECTION_LIMIT)
            .map(::toHomeAnime)
    }

    suspend fun loadTrendingPage(
        offset: Int,
        limit: Int = HOME_FULL_SECTION_LIMIT,
        filter: TrendingFilter = TrendingFilter.All,
    ): List<Anime> {
        AppLogger.d(TAG, "loadTrendingPage: offset=$offset, limit=$limit, filter=$filter")
        val catalog = currentSource().search(
            AnimeSearchRequest(
                limit = limit,
                offset = offset,
                sort = AnimeSearchSort.RATING,
                typeAliases = listOfNotNull(filter.typeAlias),
            ),
        )
        AppLogger.d(TAG, "loadTrendingPage: got ${catalog.size} items from getCatalog")
        return catalog.map(::toHomeAnime)
    }

    suspend fun loadRandomAnime(excludedIds: Set<String>): Anime? {
        ensureInternetConnection()
        repeat(RANDOM_CATALOG_ATTEMPTS) {
            val catalog = currentSource().search(
                AnimeSearchRequest(
                    limit = RANDOM_CATALOG_PAGE_SIZE,
                    offset = Random.nextInt(RANDOM_CATALOG_MAX_OFFSET),
                    sort = RANDOM_CATALOG_SORTS.random(),
                ),
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
            runCatching { currentSource().details(anime.id) }
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

        val status = title.releaseStatus.localizedDisplayName(preferEnglish())
        val isAnnouncement = status.isAnnouncementStatus()
        return Anime(
            id = title.id,
            title = displayTitle(title),
            subtitle = subtitle,
            episodesLabel = if (isAnnouncement) {
                announcementLabel()
            } else {
                (title.availableEpisodeCount
                    ?: title.episodeCount.takeIf { title.releaseStatus == AnimeReleaseStatus.RELEASED })
                    ?.let(::episodesCountLabel)
                    .orEmpty()
            },
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

    private fun sourceLanguage(): String = if (preferEnglish()) "en" else "ru"

    private fun displayTitle(title: AnimeTitle): String {
        return title.localizedDisplayName(
            languageMode = appPreferences.state.value.languageMode,
            systemLanguage = appContext.resources.configuration.locales[0]?.language,
        )
    }

    private fun selectedSourceId(): SourceId = AppPreferences.readState(appContext).animeSource

    private fun currentSource(): AnimeSourceRuntime = sourceManager.current()

    private fun isRussianLocale(): Boolean = !preferEnglish()

    private fun announcementLabel(): String = if (isRussianLocale()) "анонс" else "announcement"

    private fun episodesCountLabel(count: Int): String {
        return if (isRussianLocale()) {
            "$count серий"
        } else {
            "$count episodes"
        }
    }

    private fun trendingOffsetForSeed(selectionSeed: Long): Int {
        return Random(selectionSeed).nextInt(
            from = 0,
            until = HOME_TRENDING_MAX_OFFSET_EXCLUSIVE,
        )
    }

    private companion object {
        const val TAG = "HomeRepository"
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
        val RANDOM_CATALOG_SORTS = AnimeSearchSort.entries
    }

    private data class CachedHomeContent(
        val selectionSeed: Long,
        val languageKey: String,
        val featuredAnime: List<Anime>,
        val trending: List<Anime>,
        val recentlyUpdated: List<Anime>,
    )

    private data class CachedSourceAnime(
        val sourceId: SourceId,
        val items: List<Anime>,
    )
}
