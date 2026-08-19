package org.akkirrai.hibiki.home.data

import org.akkirrai.hibiki.home.*
import org.akkirrai.hibiki.home.state.HomeUiState

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.akkirrai.hibiki.catalog.AnimeCatalogQuery
import org.akkirrai.hibiki.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.library.LibraryRepository
import org.akkirrai.hibiki.profile.LocalWatchStateRepository
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.search.model.AnimeSearchFilters

/** Home data adapter for hosts that already expose the shared catalog and library contracts. */
class CatalogBackedHomeDataRepository(
    private val catalogRepository: AnimeCatalogRepository,
    private val libraryRepository: LibraryRepository,
    private val watchStateRepository: LocalWatchStateRepository? = null,
) : HomeDataRepository {
    override suspend fun refreshHomeState(): HomeUiState = loadHomeState()

    override suspend fun loadLocalHomeState(): HomeUiState {
        val libraryEntries = libraryRepository.getEntries()
        val libraryAnimeById = libraryEntries
            .filter { it.category != LibraryCategory.Saved }
            .associateBy { it.anime.id }
        val recentlyWatched = watchStateRepository
            ?.getAllEpisodeProgress()
            ?.groupBy { it.titleId }
            ?.mapNotNull { (titleId, progress) ->
                val anime = libraryAnimeById[titleId]?.anime ?: return@mapNotNull null
                anime to progress.maxOfOrNull { it.updatedAt }
            }
            ?.sortedByDescending { it.second ?: Long.MIN_VALUE }
            ?.map { it.first }
            .orEmpty()
        return HomeUiState(
            // Drop the first entry -- it's already spotlighted as continueAnime below, so
            // repeating it in the "recently watched" row would show the same title twice.
            recentlyWatched = recentlyWatched.drop(1),
            continueAnime = recentlyWatched.firstOrNull(),
            // Recent is a hidden bookkeeping flag (auto-assigned the moment playback starts,
            // not a deliberate user action), so it shouldn't count as "recently added".
            recentlyAddedToLibrary = libraryEntries
                .filter { it.category != LibraryCategory.Saved && it.category != LibraryCategory.Recent }
                .sortedByDescending { it.addedAt ?: Long.MIN_VALUE }
                .map { it.anime },
        )
    }

    override suspend fun loadHomeState(): HomeUiState = coroutineScope {
        // Local sections (continue watching, recently added) don't depend on the network at
        // all -- run them alongside the two catalog calls instead of behind them, and run the
        // two catalog calls concurrently with each other too instead of one after the other.
        val localDeferred = async { loadLocalHomeState() }
        val recentlyUpdatedDeferred = async {
            runCatching { catalogRepository.latest(HOME_SECTION_PAGE_SIZE) }.getOrDefault(emptyList())
        }
        val popularDeferred = async {
            runCatching {
                catalogRepository.search(
                    AnimeCatalogQuery(
                        pageSize = HOME_SECTION_PAGE_SIZE,
                        filters = AnimeSearchFilters(sortAlias = "popular"),
                    ),
                ).items
            }.getOrDefault(emptyList())
        }
        val popular = popularDeferred.await()
        localDeferred.await().copy(
            recentlyUpdated = recentlyUpdatedDeferred.await(),
            trending = popular,
            popular = popular,
        )
    }

    override suspend fun search(query: String): List<Anime> =
        search(query, AnimeSearchFilters(), limit = 20, offset = 0)

    override suspend fun search(
        query: String,
        filters: AnimeSearchFilters,
        limit: Int,
        offset: Int,
    ): List<Anime> = catalogRepository.search(
        AnimeCatalogQuery(
            text = query,
            page = (offset / limit.coerceAtLeast(1)) + 1,
            pageSize = limit,
            filters = filters,
        ),
    ).items

    override suspend fun getSearchFilterCatalog(): AnimeCatalogFilterCatalog =
        catalogRepository.filterCatalog()

    override suspend fun loadRecentlyUpdatedPage(offset: Int, limit: Int): List<Anime> =
        catalogRepository.search(
            AnimeCatalogQuery(
                page = (offset / limit.coerceAtLeast(1)) + 1,
                pageSize = limit,
                filters = AnimeSearchFilters(sortAlias = "updated"),
            ),
        ).items

    override suspend fun loadTrendingPage(
        offset: Int,
        limit: Int,
        filterTypeAlias: String?,
    ): List<Anime> = catalogRepository.search(
        AnimeCatalogQuery(
            page = (offset / limit.coerceAtLeast(1)) + 1,
            pageSize = limit,
            filters = AnimeSearchFilters(sortAlias = "popular", typeAlias = filterTypeAlias),
        ),
    ).items

    override suspend fun loadRandomAnime(excludedIds: Set<String>): Anime? =
        catalogRepository.search(AnimeCatalogQuery(pageSize = 1)).items.firstOrNull { it.id !in excludedIds }

    override suspend fun enrichDescriptions(items: List<Anime>): List<Anime> = items.map { enrichDescription(it) }

    override suspend fun enrichDescription(anime: Anime): Anime =
        catalogRepository.getDetails(anime.id, anime)

    override fun close() = Unit

    private companion object {
        const val HOME_SECTION_PAGE_SIZE = 12
    }
}
