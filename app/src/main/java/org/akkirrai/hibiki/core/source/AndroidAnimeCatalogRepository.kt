package org.akkirrai.hibiki.core.source

import android.content.Context
import io.ktor.client.HttpClient
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogPage
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogQuery
import org.akkirrai.hibiki.shared.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilter
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.shared.model.Anime

/** Android data-source adapter for the platform-neutral catalog contract. */
class AndroidAnimeCatalogRepository(
    private val delegate: AnimeSearchRepository,
) : AnimeCatalogRepository {
    constructor(context: Context, client: HttpClient? = null) : this(
        delegate = if (client == null) {
            AnimeSearchRepository(context.applicationContext)
        } else {
            AnimeSearchRepository(context.applicationContext, client)
        },
    )

    override val initialItems: List<Anime> = emptyList()

    override suspend fun getDetails(id: String, fallback: Anime): Anime = delegate.getDetails(id, fallback)

    override suspend fun filterCatalog(): AnimeCatalogFilterCatalog =
        delegate.getSearchFilterCatalog().let { catalog ->
            AnimeCatalogFilterCatalog(
                sortOptions = catalog.sortOptions.map { option -> AnimeCatalogFilterOption(option.id, option.title) },
                typeOptions = catalog.typeOptions.map { option -> AnimeCatalogFilterOption(option.id, option.title) },
                statusOptions = catalog.statusOptions.map { option -> AnimeCatalogFilterOption(option.id, option.title) },
                genreOptions = catalog.genreOptions.map { option -> AnimeCatalogFilterOption(option.id, option.title) },
                capabilities = AnimeCatalogCapabilities(
                    supportedSorts = catalog.capabilities.supportedSorts.map { it.name.lowercase() }.toSet(),
                    supportedFilters = catalog.capabilities.supportedFilters.mapNotNull { filter ->
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

    override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage {
        val filters = query.filters
        val items = delegate.search(
            AnimeSearchRequest(
                query = query.text,
                limit = query.pageSize,
                offset = query.offset,
                sort = when (filters.sortAlias.lowercase()) {
                    "alphabetical", "title" -> AnimeSearchSort.TITLE
                    "popular", "rating" -> AnimeSearchSort.RATING
                    else -> AnimeSearchSort.RELEVANCE
                },
                typeAliases = listOfNotNull(filters.typeAlias),
                statusAliases = listOfNotNull(filters.statusAlias),
                includedGenreAliases = filters.includedGenreAliases.sorted(),
                excludedGenreAliases = filters.excludedGenreAliases.sorted(),
                yearFrom = filters.yearFrom,
                yearTo = filters.yearTo,
            ),
        )
        return AnimeCatalogPage(
            items = items,
            page = query.page.coerceAtLeast(1),
            canLoadMore = items.size >= query.pageSize.coerceAtLeast(1),
        )
    }

    fun close() = delegate.close()
}
