package org.akkirrai.hibiki.feature.home

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random
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
import org.akkirrai.hibiki.shared.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilter
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.shared.home.HomeDataRepository
import org.akkirrai.hibiki.shared.home.resolveDisplayTypeLabel
import org.akkirrai.hibiki.shared.home.formatEpisodesCountLabel
import org.akkirrai.hibiki.shared.home.formatAnnouncementLabel
import org.akkirrai.hibiki.shared.home.resolveTrendingOffset
import org.akkirrai.hibiki.shared.settings.isEnglishAppLanguage
import org.akkirrai.hibiki.shared.settings.resolveAppLanguageTag
import org.akkirrai.hibiki.shared.home.SearchSortAlias
import org.akkirrai.hibiki.shared.home.resolveSearchSortAlias
import org.akkirrai.hibiki.shared.details.isAnnouncementStatus

class HomeRepository(
    context: Context,
    private val client: HttpClient = AndroidHttpClientFactory.create(),
) : HomeDataRepository {
    @Volatile
    private var cachedRecentUpdates: CachedSourceAnime? = null

    private val appContext = context.applicationContext
    private val appPreferences = AppPreferences(appContext)
    private val sourceManager = AnimeSourceRuntimeManager(appContext, client)
    private val searchRepository = AnimeSearchRepository(appContext, client)
    private val watchStateRepository = WatchStateRepository(appContext)
    private val offlineTitleMetadataRepository = OfflineTitleMetadataRepository(appContext)
    private val libraryRepository = LibraryRepository(appContext)

    override fun fallbackHomeState(): HomeUiState {
        val continueAnime = loadStoredContinueAnime()
        return HomeUiState(
            continueAnime = continueAnime,
            recentlyWatched = loadRecentlyWatched(excludedTitleId = continueAnime?.id),
            recentlyAddedToLibrary = loadRecentlyAddedToLibrary(),
        )
    }

    override suspend fun refreshHomeState(): HomeUiState {
        AppLogger.d(TAG, "refreshHomeState: refreshing local personal content")
        return loadHomeState()
    }

    override suspend fun loadHomeState(): HomeUiState {
        val continueAnime = loadStoredContinueAnime()
        return HomeUiState(
            continueAnime = continueAnime,
            recentlyWatched = loadRecentlyWatched(excludedTitleId = continueAnime?.id),
            recentlyAddedToLibrary = loadRecentlyAddedToLibrary(),
        )

    }

    override suspend fun search(query: String): List<Anime> {
        AppLogger.d(TAG, "search(query=$query)")
        ensureInternetConnection()
        return searchRepository.search(query)
    }

    override suspend fun search(
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

    override suspend fun getSearchFilterCatalog(): AnimeCatalogFilterCatalog =
        searchRepository.getSearchFilterCatalog().toShared()

    override fun close() {
        searchRepository.close()
    }

    private fun AnimeSearchFilterCatalog.toShared(): AnimeCatalogFilterCatalog =
        AnimeCatalogFilterCatalog(
            sortOptions = sortOptions.map { it.toShared() },
            typeOptions = typeOptions.map { it.toShared() },
            statusOptions = statusOptions.map { it.toShared() },
            genreOptions = genreOptions.map { it.toShared() },
            capabilities = AnimeCatalogCapabilities(
                supportedSorts = capabilities.supportedSorts.map { it.name.lowercase() }.toSet(),
                supportedFilters = capabilities.supportedFilters.map { AnimeCatalogFilter.valueOf(it.name) }.toSet(),
            ),
        )

    private fun org.akkirrai.beakokit.model.SearchFilterOption.toShared(): AnimeCatalogFilterOption =
        AnimeCatalogFilterOption(id = id, title = title)

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

    private fun loadRecentlyWatched(excludedTitleId: String? = null): List<Anime> =
        watchStateRepository
            .getRecentTitleWatchStates(
                HOME_PERSONAL_SECTION_LIMIT + if (excludedTitleId != null) 1 else 0,
            )
            .filterNot { state -> state.titleId == excludedTitleId }
            .mapNotNull { state -> findStoredAnime(state.titleId) }
            .distinctBy(Anime::id)

    private fun loadRecentlyAddedToLibrary(): List<Anime> =
        libraryRepository.getLibraryEntries()
            .sortedByDescending { it.addedAt ?: Long.MIN_VALUE }
            .distinctBy { it.anime.id }
            .take(HOME_PERSONAL_SECTION_LIMIT)
            .map { it.anime }

    private fun ensureInternetConnection() {
        if (!hasActiveInternetConnection(appContext)) {
            throw NoInternetConnectionException(appContext.getString(R.string.home_error_no_internet))
        }
    }

    override suspend fun loadRecentlyUpdatedPage(
        offset: Int,
        limit: Int,
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
        loadRecentlyUpdatedPage(offset = 0, limit = HOME_SECTION_LIMIT)

    private suspend fun loadRecentlyUpdatedCatalog(): List<Anime> {
        return currentSource()
            .latest(limit = HOME_FULL_SECTION_LIMIT)
            .map(::toHomeAnime)
    }

    private suspend fun loadSourceTrending(selectionSeed: Long): List<Anime> {
        val source = currentSource()
        val trendingOffset = if (source.source.catalogCapabilities.supports(AnimeSearchSort.RATING)) {
            resolveTrendingOffset(selectionSeed, HOME_TRENDING_MAX_OFFSET_EXCLUSIVE)
        } else {
            0
        }
        AppLogger.d(TAG, "loadSourceTrending: limit=$HOME_TRENDING_WINDOW_SIZE, offset=$trendingOffset")
        return source.search(
            AnimeSearchRequest(
                limit = HOME_TRENDING_WINDOW_SIZE,
                offset = trendingOffset,
                sort = AnimeSearchSort.RATING,
            ),
        ).map(::toHomeAnime)
    }

    override suspend fun loadTrendingPage(
        offset: Int,
        limit: Int,
        filterTypeAlias: String?,
    ): List<Anime> {
        AppLogger.d(TAG, "loadTrendingPage: offset=$offset, limit=$limit, filter=$filterTypeAlias")
        val catalog = currentSource().search(
            AnimeSearchRequest(
                limit = limit,
                offset = offset,
                sort = AnimeSearchSort.RATING,
                typeAliases = listOfNotNull(filterTypeAlias),
            ),
        )
        AppLogger.d(TAG, "loadTrendingPage: got ${catalog.size} items from getCatalog")
        return catalog.map(::toHomeAnime)
    }

    override suspend fun loadRandomAnime(excludedIds: Set<String>): Anime? {
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

    override suspend fun enrichDescriptions(items: List<Anime>): List<Anime> {
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

    override suspend fun enrichDescription(anime: Anime): Anime =
        searchRepository.getDetails(anime.id, anime)

    private fun toHomeAnime(title: AnimeTitle): Anime {
        val subtitle = buildList {
            title.type?.let(::resolveDisplayTypeLabel)?.let(::add)
            title.year?.toString()?.let(::add)
        }.joinToString(" · ")

        val status = title.releaseStatus.localizedDisplayName(preferEnglish())
        val isAnnouncement = isAnnouncementStatus(status)
        return Anime(
            id = title.id,
            title = displayTitle(title),
            subtitle = subtitle,
            episodesLabel = if (isAnnouncement) {
                formatAnnouncementLabel(preferEnglish())
            } else {
                (title.availableEpisodeCount
                    ?: title.episodeCount.takeIf { title.releaseStatus == AnimeReleaseStatus.RELEASED })
                    ?.let { count -> formatEpisodesCountLabel(count, preferEnglish()) }
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

    private fun String.toSearchSort(): AnimeSearchSort = when (resolveSearchSortAlias(this)) {
        SearchSortAlias.RELEVANCE -> AnimeSearchSort.RELEVANCE
        SearchSortAlias.RATING -> AnimeSearchSort.RATING
        SearchSortAlias.TITLE -> AnimeSearchSort.TITLE
        SearchSortAlias.YEAR -> AnimeSearchSort.YEAR
        SearchSortAlias.VOTES -> AnimeSearchSort.VOTES
        SearchSortAlias.VIEWS -> AnimeSearchSort.VIEWS
        SearchSortAlias.COMMENTS -> AnimeSearchSort.COMMENTS
        }

    private fun preferEnglish(): Boolean {
        return isEnglishAppLanguage(
            appPreferences.state.value.languageMode,
            appContext.resources.configuration.locales[0]?.language.orEmpty(),
        )
    }

    private fun sourceLanguage(): String = resolveAppLanguageTag(
        appPreferences.state.value.languageMode,
        appContext.resources.configuration.locales[0]?.language.orEmpty(),
    )

    private fun displayTitle(title: AnimeTitle): String = title.displayName

    private fun selectedSourceId(): SourceId = AppPreferences.readState(appContext).animeSource

    private fun currentSource(): AnimeSourceRuntime = sourceManager.current()

    private companion object {
        const val TAG = "HomeRepository"
        const val HOME_PERSONAL_SECTION_LIMIT = 12
        const val HOME_SECTION_LIMIT = 12
        const val HOME_FULL_SECTION_LIMIT = 100
        const val HOME_TRENDING_WINDOW_SIZE = 24
        const val HOME_TRENDING_MAX_OFFSET_EXCLUSIVE = 201
        const val RANDOM_CATALOG_PAGE_SIZE = 40
        const val RANDOM_CATALOG_MAX_OFFSET = 5_000
        const val RANDOM_CATALOG_ATTEMPTS = 5
        val RANDOM_CATALOG_SORTS = AnimeSearchSort.entries
    }

    private data class CachedSourceAnime(
        val sourceId: SourceId,
        val items: List<Anime>,
    )
}
