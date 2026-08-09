package org.akkirrai.hibiki.shared.catalog

import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.ExternalSourceRegistry
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceLogLevel
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.SearchFilterOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilter
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.shared.source.ExternalAnimeStatusLabels
import org.akkirrai.hibiki.shared.source.toAppAnime

/** Shared catalog adapter for installed external sources during the transition period. */
class ExternalSourceCatalogRepository(
    private val registryProvider: () -> ExternalSourceRegistry?,
    private val registryAwaiter: (suspend () -> ExternalSourceRegistry?)? = null,
    private val contextProvider: (SourceId) -> SourceContext,
    private val statusLabels: ExternalAnimeStatusLabels,
    initialSourceId: SourceId? = null,
) : MultiSourceAnimeCatalogRepository {
    private var selectedSourceId: SourceId? = initialSourceId
    private val sourceCache = mutableMapOf<SourceId, AnimeSource>()
    private val sourceCacheMutex = Mutex()

    override val initialItems: List<Anime> = emptyList()

    override fun invalidate() {
        sourceCache.clear()
    }

    override fun canContinuePaginationAfterShortPage(): Boolean = true

    fun hasSource(sourceId: String): Boolean = registryProvider()?.sources
        ?.any { it.id.value == sourceId }
        ?: false

    override fun selectSource(sourceId: String) {
        selectedSourceId = SourceId(sourceId)
    }

    override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage =
        searchSource(requireSelectedSource(), query)

    override suspend fun filterCatalog(): AnimeCatalogFilterCatalog {
        val source = source(requireSelectedSource())
        val externalCatalog = withContext(Dispatchers.Default) {
            source.getSearchFilterCatalog()
        }
        val capabilities = source.catalogCapabilities
        val sortOptions = (externalCatalog.sortOptions + capabilities.supportedSorts.map { sort ->
            SearchFilterOption(sort.name.lowercase(), sort.label())
        }).distinctBy(SearchFilterOption::id)
        return AnimeCatalogFilterCatalog(
            sortOptions = sortOptions.map { AnimeCatalogFilterOption(it.id, it.title) },
            typeOptions = externalCatalog.typeOptions.map { AnimeCatalogFilterOption(it.id, it.title) },
            statusOptions = externalCatalog.statusOptions.map { AnimeCatalogFilterOption(it.id, it.title) },
            genreOptions = externalCatalog.genreOptions.map { AnimeCatalogFilterOption(it.id, it.title) },
            capabilities = AnimeCatalogCapabilities(
                supportedSorts = capabilities.supportedSorts.map { it.name.lowercase() }.toSet(),
                supportedFilters = capabilities.supportedFilters.mapNotNull { filter ->
                    when (filter.name) {
                        "TYPE" -> AnimeCatalogFilter.TYPE
                        "STATUS" -> AnimeCatalogFilter.STATUS
                        "INCLUDED_GENRES" -> AnimeCatalogFilter.INCLUDED_GENRES
                        "EXCLUDED_GENRES" -> AnimeCatalogFilter.EXCLUDED_GENRES
                        "YEAR_RANGE" -> AnimeCatalogFilter.YEAR_RANGE
                        else -> null
                    }
                }.toSet(),
            ),
        )
    }

    override suspend fun searchSource(
        sourceId: String,
        query: AnimeCatalogQuery,
    ): AnimeCatalogPage = searchSource(SourceId(sourceId), query)

    private suspend fun searchSource(
        sourceId: SourceId,
        query: AnimeCatalogQuery,
    ): AnimeCatalogPage {
        val source = source(sourceId)
        val filters = query.filters
        val request = source.catalogCapabilities.adapt(
            AnimeSearchRequest(
                query = query.text,
                limit = query.pageSize.coerceAtLeast(1),
                offset = query.offset,
                sort = filters.sortAlias.toExternalSort(),
                typeAliases = listOfNotNull(filters.typeAlias),
                statusAliases = listOfNotNull(filters.statusAlias),
                includedGenreAliases = filters.includedGenreAliases.sorted(),
                excludedGenreAliases = filters.excludedGenreAliases.sorted(),
                yearFrom = filters.yearFrom,
                yearTo = filters.yearTo,
            ),
        )
        val logger = contextProvider(sourceId).logger
        logger.log(
            SourceLogLevel.DEBUG,
            "External catalog request: source=${sourceId.value}, query=${query.text.take(80)}, " +
                "page=${query.page}, offset=${request.offset}, limit=${request.limit}, " +
                "sort=${request.sort}, filters=${query.filters}",
            null,
        )
        val items = withContext(Dispatchers.Default) { source.search(request) }.map { title ->
            title.toAppAnime(
                sourceId = sourceId,
                preferEnglish = SourceLanguage.ENGLISH in contextProvider(sourceId).preferredLanguages,
                statusLabels = statusLabels,
            )
        }
        logger.log(
            if (items.isEmpty()) SourceLogLevel.WARNING else SourceLogLevel.DEBUG,
            "External catalog response: source=${sourceId.value}, items=${items.size}, " +
                "page=${query.page}, offset=${request.offset}, limit=${request.limit}",
            null,
        )
        return AnimeCatalogPage(
            items = items,
            page = query.page.coerceAtLeast(1),
            canLoadMore = items.size >= request.limit,
        )
    }

    override suspend fun getDetails(id: String, fallback: Anime): Anime {
        val key = AnimeKey.parse(id)
        val sourceId = key?.sourceId ?: requireSelectedSource()
        val title = withContext(Dispatchers.Default) {
            source(sourceId).getById(key?.takeIf { it.sourceId == sourceId }?.nativeId ?: id)
        }
        return title.toAppAnime(
            sourceId = sourceId,
            preferEnglish = SourceLanguage.ENGLISH in contextProvider(sourceId).preferredLanguages,
            statusLabels = statusLabels,
        )
    }

    private fun requireSelectedSource(): SourceId =
        selectedSourceId ?: error("An external source must be selected before searching")

    private suspend fun source(sourceId: SourceId): AnimeSource =
        withContext(Dispatchers.Default) {
            sourceCacheMutex.withLock {
                sourceCache[sourceId] ?: run {
                    val registry = registryProvider() ?: registryAwaiter?.invoke()
                    val created = registry?.create(sourceId, contextProvider(sourceId))
                        ?: error("External source is not installed: $sourceId")
                    sourceCache[sourceId] = created
                    created
                }
            }
        }

    private fun String.toExternalSort(): AnimeSearchSort = when (lowercase()) {
        "popular", "rating" -> AnimeSearchSort.RATING
        "alphabetical", "title" -> AnimeSearchSort.TITLE
        "year" -> AnimeSearchSort.YEAR
        "votes" -> AnimeSearchSort.VOTES
        "views" -> AnimeSearchSort.VIEWS
        "comments" -> AnimeSearchSort.COMMENTS
        else -> AnimeSearchSort.RELEVANCE
    }

    private fun AnimeSearchSort.label(): String = when (this) {
        AnimeSearchSort.RELEVANCE -> "Relevance"
        AnimeSearchSort.RATING -> "Rating"
        AnimeSearchSort.TITLE -> "Title"
        AnimeSearchSort.YEAR -> "Year"
        AnimeSearchSort.VOTES -> "Votes"
        AnimeSearchSort.VIEWS -> "Views"
        AnimeSearchSort.COMMENTS -> "Comments"
    }
}
