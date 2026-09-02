package org.akkirrai.hibiki.feature.home

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.random.Random
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.beakokit.model.AnimeTitle
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
import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.akkirrai.hibiki.core.source.localizedDisplayName

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
            recentlyWatched = loadRecentlyWatchedAnime().drop(1).take(RECENTLY_WATCHED_LIMIT),
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

        val source = currentSource()
        val trendingOffset = if (source.source.catalogCapabilities.supports(AnimeSearchSort.RATING)) {
            trendingOffsetForSeed(selectionSeed)
        } else {
            0
        }
        AppLogger.d(TAG, "loadHomeState: cache miss, calling getCatalog(limit=$HOME_TRENDING_WINDOW_SIZE, offset=$trendingOffset, lang=$languageKey)")
        val catalog = retryOnColdStartNetworkFailure {
            source.search(
                AnimeSearchRequest(
                    limit = HOME_TRENDING_WINDOW_SIZE,
                    offset = trendingOffset,
                    sort = AnimeSearchSort.RATING,
                ),
            )
        }
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

        val recentlyWatched = loadRecentlyWatchedAnime()
        return HomeUiState(
            featuredAnime = featuredAnime,
            continueAnime = loadContinueAnime(),
            recentlyWatched = recentlyWatched.drop(1).take(RECENTLY_WATCHED_LIMIT),
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
            loadRecentlyUpdatedCatalog().also {
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
        return coroutineScope {
            items.map { anime ->
                async {
                    if (!anime.description.isNullOrBlank()) {
                        anime
                    } else {
                        runCatching { searchRepository.getDetails(anime.id, anime) }
                            .getOrDefault(anime)
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun enrichDescription(anime: Anime): Anime =
        searchRepository.getDetails(anime.id, anime)

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
            posterFallbackUrl = title.posterFallbackUrl,
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

    private fun displayTitle(title: AnimeTitle): String = title.displayName

    private fun selectedSourceId(): SourceId = AppPreferences.readState(appContext).animeSource

    private fun currentSource(): AnimeSourceRuntime = sourceManager.current()

    private fun isRussianLocale(): Boolean = !preferEnglish()

    private fun announcementLabel(): String = if (isRussianLocale()) "анонс" else "announcement"

    private fun episodesCountLabel(count: Int): String = "$count ${episodesWord(count)}"

    private fun episodesWord(count: Int): String {
        if (!isRussianLocale()) return if (count == 1) "episode" else "episodes"
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> "серий"
            mod10 == 1 -> "серия"
            mod10 in 2..4 -> "серии"
            else -> "серий"
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
