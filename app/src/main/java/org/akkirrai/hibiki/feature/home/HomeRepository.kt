package org.akkirrai.hibiki.feature.home

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.random.Random
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.network.hasActiveInternetConnection
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.AnimeSourceRuntime
import org.akkirrai.hibiki.core.source.AnimeSourceRuntimeManager
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository

class HomeRepository(
    context: Context,
    private val client: HttpClient = AndroidHttpClientFactory.create(),
    sourceManager: AnimeSourceRuntimeManager? = null,
    closeClientOnClose: Boolean = true,
) {
    @Volatile
    private var cachedHomeContent: CachedHomeContent? = null

    @Volatile
    private var cachedRecentUpdates: CachedSourceAnime? = null

    @Volatile
    private var currentHomeSelectionSeed: Long? = null

    private val appContext = context.applicationContext
    private val appPreferences = AppPreferences(appContext)
    private val sourceManager = sourceManager ?: AnimeSourceRuntimeManager(appContext, client)
    private val searchRepository = AnimeSearchRepository(
        context = appContext,
        client = client,
        sourceManager = this.sourceManager,
        closeClientOnClose = closeClientOnClose,
    )
    private val watchStateRepository = WatchStateRepository(appContext)
    private val offlineTitleMetadataRepository = OfflineTitleMetadataRepository(appContext)
    private val libraryRepository = LibraryRepository(appContext)

    /**
     * Home content that survives a lost connection: only the rows the device already stores
     * locally (continue watching, recently watched, both resolved through the offline title
     * metadata and the library). The catalog rows are deliberately left empty - featured,
     * trending and recently updated only exist once a source request has succeeded, and filling
     * them with placeholders would show the user titles that are not in any source.
     */
    fun fallbackHomeState(): HomeUiState {
        return HomeUiState(
            featuredAnime = emptyList(),
            continueAnime = loadStoredContinueAnime(),
            recentlyWatched = loadRecentlyWatchedAnime().drop(1).take(RECENTLY_WATCHED_LIMIT),
            popular = emptyList(),
            trending = emptyList(),
            recentlyUpdated = emptyList(),
        )
    }

    suspend fun refreshHomeState(): HomeUiState {
        AppLogger.d(TAG, "refreshHomeState: clearing cache")
        ensureInternetConnection()
        cachedHomeContent = null
        cachedRecentUpdates = null
        currentHomeSelectionSeed = Random.nextLong()
        AppLogger.d(TAG, "refreshHomeState: advanced home selection seed to $currentHomeSelectionSeed")
        return loadHomeState(forceRefresh = true)
    }

    suspend fun loadHomeState(forceRefresh: Boolean = false): HomeUiState {
        AppLogger.d(TAG, "loadHomeState: called")
        val selectionSeed = currentHomeSelectionSeed ?: Random.nextLong().also {
            currentHomeSelectionSeed = it
        }
        val languageKey = "${selectedSourceId().value}:${sourceLanguage()}"
        if (!forceRefresh) cachedHomeContent?.let { cached ->
            if (cached.selectionSeed == selectionSeed && cached.languageKey == languageKey) {
                AppLogger.d(TAG, "loadHomeState: using cachedHomeContent — " +
                    "trending=${cached.trending.size}, recentlyUpdated=${cached.recentlyUpdated.size}, seed=$selectionSeed, lang=$languageKey")
                val recentlyWatched = loadRecentlyWatchedAnime()
                return HomeUiState(
                    featuredAnime = cached.featuredAnime,
                    continueAnime = loadContinueAnime(),
                    recentlyWatched = recentlyWatched.drop(1).take(RECENTLY_WATCHED_LIMIT),
                    popular = emptyList(),
                    trending = cached.trending,
                    recentlyUpdated = cached.recentlyUpdated,
                )
            }
        }

        ensureInternetConnection()
        return coroutineScope {
            val recentlyUpdatedDeferred = async {
                AppLogger.d(TAG, "loadHomeState: calling loadRecentlyUpdated()")
                runCatching { loadRecentlyUpdated(forceRefresh) }
                    .onFailure { error ->
                        AppLogger.w(
                            TAG,
                            "loadHomeState: recent updates are unavailable: ${error.message}",
                        )
                    }
                    .getOrDefault(emptyList())
            }
            val continueAnimeDeferred = async { loadContinueAnime() }

            val source = currentSource()
            val trendingOffset = if (source.source.catalogCapabilities.supports(AnimeSearchSort.RATING)) {
                trendingOffsetForSeed(selectionSeed)
            } else {
                0
            }
            AppLogger.d(TAG, "loadHomeState: cache miss, calling getCatalog(limit=$HOME_TRENDING_WINDOW_SIZE, offset=$trendingOffset, lang=$languageKey)")
            val catalog = retryOnColdStartNetworkFailure {
                searchRepository.search(
                    AnimeSearchRequest(
                        limit = HOME_TRENDING_WINDOW_SIZE,
                        offset = trendingOffset,
                        sort = AnimeSearchSort.RATING,
                    ),
                    allowEmptyQuery = true,
                    forceRefresh = forceRefresh,
                )
            }
            AppLogger.d(TAG, "loadHomeState: getCatalog returned ${catalog.size} items")

            if (catalog.isEmpty()) {
                AppLogger.w(TAG, "loadHomeState: catalog empty")
                throw IllegalStateException(appContext.getString(R.string.home_error_load_failed))
            }

            val homeWindow = catalog
            val featuredAnime = homeWindow
                .shuffled(Random(selectionSeed xor FEATURED_ROTATION_SEED_SALT))
                .take(FEATURED_COUNT)
            val featuredIds = featuredAnime.mapTo(mutableSetOf()) { it.id }
            val trending = homeWindow
                .shuffled(Random(selectionSeed xor TRENDING_ROTATION_SEED_SALT))
                .filterNot { it.id in featuredIds }
                .take(HOME_SECTION_LIMIT)
            val recentlyUpdated = recentlyUpdatedDeferred.await()
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

            val recentlyWatched = loadRecentlyWatchedAnime()
            HomeUiState(
                featuredAnime = featuredAnime,
                continueAnime = continueAnimeDeferred.await(),
                recentlyWatched = recentlyWatched.drop(1).take(RECENTLY_WATCHED_LIMIT),
                popular = emptyList(),
                trending = trending,
                recentlyUpdated = recentlyUpdated,
            )
        }
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

    /** Mirrors the previous Home feed: the active title is featured above, not duplicated here. */
    private fun loadRecentlyWatchedAnime(): List<Anime> = watchStateRepository
        .getAllEpisodeProgress()
        .groupBy { progress -> progress.titleId }
        .mapNotNull { (titleId, progress) ->
            findStoredAnime(titleId)?.let { anime ->
                anime to progress.maxOfOrNull { item -> item.updatedAt }
            }
        }
        .sortedByDescending { (_, updatedAt) -> updatedAt ?: Long.MIN_VALUE }
        .map { (anime, _) -> anime }

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

    /**
     * Retries a source call a couple of times on a transient network failure before giving up.
     * [hasActiveInternetConnection] can report the network as up moments before DNS/routing is
     * actually usable - most visibly right after the app process starts (e.g. installed via adb
     * with the screen off), where the very first request can fail with a raw connectivity error
     * even though [ensureInternetConnection] just passed. A short, bounded retry absorbs that race
     * instead of surfacing a hard error the user has to manually dismiss with Retry.
     */
    private suspend fun <T> retryOnColdStartNetworkFailure(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (error: Exception) {
                if (attempt >= COLD_START_RETRY_ATTEMPTS || !error.isTransientNetworkFailure()) throw error
                attempt++
                AppLogger.w(TAG, "retryOnColdStartNetworkFailure: attempt $attempt after ${error.message}")
                delay(COLD_START_RETRY_DELAY_MILLIS * attempt)
            }
        }
    }

    private fun Throwable.isTransientNetworkFailure(): Boolean = when (this) {
        is java.io.IOException -> true
        is SourceException -> kind == SourceErrorKind.NETWORK || kind == SourceErrorKind.UNAVAILABLE
        else -> false
    }

    suspend fun loadRecentlyUpdatedPage(
        offset: Int,
        limit: Int = HOME_SECTION_LIMIT,
        forceRefresh: Boolean = false,
    ): List<Anime> {
        val sourceId = selectedSourceId()
        val catalog = if (forceRefresh) {
            loadRecentlyUpdatedCatalog(forceRefresh = true).also {
                cachedRecentUpdates = CachedSourceAnime(sourceId, it)
            }
        } else {
            cachedRecentUpdates
                ?.takeIf { it.sourceId == sourceId }
                ?.items
                ?: loadRecentlyUpdatedCatalog().also {
                    cachedRecentUpdates = CachedSourceAnime(sourceId, it)
                }
        }
        return catalog.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(1))
    }

    private suspend fun loadRecentlyUpdated(forceRefresh: Boolean = false): List<Anime> =
        // Home renders only one short row. Parsing the 100-item pagination snapshot here delayed
        // first paint even though 88 entries were immediately discarded; the catalog keeps its
        // own full snapshot through loadRecentlyUpdatedPage when the user actually opens it.
        searchRepository.latest(limit = HOME_SECTION_LIMIT, forceRefresh = forceRefresh)

    private suspend fun loadRecentlyUpdatedCatalog(forceRefresh: Boolean = false): List<Anime> {
        return searchRepository.latest(limit = HOME_FULL_SECTION_LIMIT, forceRefresh = forceRefresh)
    }

    suspend fun loadTrendingPage(
        offset: Int,
        limit: Int = HOME_FULL_SECTION_LIMIT,
        filter: TrendingFilter = TrendingFilter.All,
    ): List<Anime> {
        AppLogger.d(TAG, "loadTrendingPage: offset=$offset, limit=$limit, filter=$filter")
        val catalog = searchRepository.search(
            AnimeSearchRequest(
                limit = limit,
                offset = offset,
                sort = AnimeSearchSort.RATING,
                typeAliases = listOfNotNull(filter.typeAlias),
            ),
            allowEmptyQuery = true,
        )
        AppLogger.d(TAG, "loadTrendingPage: got ${catalog.size} items from getCatalog")
        return catalog
    }

    suspend fun loadRandomAnime(excludedIds: Set<String>): Anime? {
        ensureInternetConnection()
        repeat(RANDOM_CATALOG_ATTEMPTS) {
            val catalog = searchRepository.search(
                AnimeSearchRequest(
                    limit = RANDOM_CATALOG_PAGE_SIZE,
                    offset = Random.nextInt(RANDOM_CATALOG_MAX_OFFSET),
                    sort = RANDOM_CATALOG_SORTS.random(),
                ),
                allowEmptyQuery = true,
            )
            val candidates = catalog
                .filterNot { it.id in excludedIds }
            candidates.randomOrNull()?.let { return it }
        }
        return null
    }

    suspend fun enrichDescription(anime: Anime): Anime =
        searchRepository.getDetails(anime.id, anime)

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
            LanguageMode.ENGLISH, LanguageMode.UKRAINIAN -> true
            LanguageMode.RUSSIAN -> false
            LanguageMode.SYSTEM -> appContext.resources.configuration.locales[0]?.language != "ru"
        }
    }

    private fun sourceLanguage(): String = if (preferEnglish()) "en" else "ru"

    private fun selectedSourceId(): SourceId = AppPreferences.readState(appContext).animeSource

    private fun currentSource(): AnimeSourceRuntime = sourceManager.current()

    private fun trendingOffsetForSeed(selectionSeed: Long): Int {
        return Random(selectionSeed).nextInt(
            from = 0,
            until = HOME_TRENDING_MAX_OFFSET_EXCLUSIVE,
        )
    }

    private companion object {
        const val TAG = "HomeRepository"
        const val HOME_SECTION_LIMIT = 12
        const val RECENTLY_WATCHED_LIMIT = 15
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
        const val COLD_START_RETRY_ATTEMPTS = 2
        const val COLD_START_RETRY_DELAY_MILLIS = 400L
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
