package org.akkirrai.hibiki.core.source

import android.content.Context
import io.ktor.client.HttpClient
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogPage
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogQuery
import org.akkirrai.hibiki.shared.model.Anime

/** Android data-source adapter for the platform-neutral catalog contract. */
class AndroidAnimeCatalogRepository(
    context: Context,
    client: HttpClient? = null,
) : AnimeCatalogRepository {
    private val delegate = if (client == null) {
        AnimeSearchRepository(context.applicationContext)
    } else {
        AnimeSearchRepository(context.applicationContext, client)
    }

    override val initialItems: List<Anime> = emptyList()

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
