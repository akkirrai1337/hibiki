package org.akkirrai.hibiki.desktop.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.akkirrai.beakokit.http.BeakoKitHttpPolicy
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.source.BuiltInSources
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogPage
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogQuery
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilter
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.shared.catalog.model.AnimeRating
import org.akkirrai.hibiki.shared.catalog.model.AnimeTrailer

/** JVM host adapter that exposes the existing BeakoKit source through shared CMP models. */
class DesktopCatalogRepository(initialSourceId: String? = null) : AnimeCatalogRepository, AutoCloseable {
    private val client = HttpClient(CIO) {
        installBeakoKitHttpDefaults(BeakoKitHttpPolicy(userAgent = "Hibiki/0.1 Desktop"))
    }
    private val knownSourceIds = BuiltInSources.catalog.sources.map { it.id }.toSet()
    private var activeSourceId = initialSourceId
        ?.let(::SourceId)
        ?.takeIf { it in knownSourceIds }
        ?: BuiltInSources.YUMMY_ANIME_ID
    private val sources = mutableMapOf<SourceId, AnimeSource>()

    override val initialItems: List<Anime> = emptyList()

    override fun selectSource(sourceId: String) {
        SourceId(sourceId).takeIf { it in knownSourceIds }?.let { activeSourceId = it }
    }

    private fun activeSource(): AnimeSource = sources.getOrPut(activeSourceId) {
        BuiltInSources.catalog.create(
            activeSourceId,
            DefaultSourceContext(
                httpClient = client,
                preferredLanguages = listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
            ),
        )
    }

    override suspend fun getDetails(id: String, fallback: Anime): Anime =
        activeSource().getById(id).toSharedAnime()

    override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage {
        val filters = query.filters
        val titles = activeSource().search(
            AnimeSearchRequest(
                query = query.text,
                limit = query.pageSize,
                offset = query.offset,
                sort = when (filters.sortAlias.lowercase()) {
                    "alphabetical", "title" -> org.akkirrai.beakokit.model.AnimeSearchSort.TITLE
                    "popular", "rating" -> org.akkirrai.beakokit.model.AnimeSearchSort.RATING
                    else -> org.akkirrai.beakokit.model.AnimeSearchSort.RELEVANCE
                },
                typeAliases = listOfNotNull(filters.typeAlias),
                statusAliases = listOfNotNull(filters.statusAlias),
                includedGenreAliases = filters.includedGenreAliases.sorted(),
                excludedGenreAliases = filters.excludedGenreAliases.sorted(),
                yearFrom = filters.yearFrom,
                yearTo = filters.yearTo,
            ),
        )
        val items = titles.map { it.toSharedAnime() }
        return AnimeCatalogPage(
            items = items,
            page = query.page.coerceAtLeast(1),
            canLoadMore = items.size >= query.pageSize.coerceAtLeast(1),
        )
    }

    override suspend fun filterCatalog(): AnimeCatalogFilterCatalog = activeSource().getSearchFilterCatalog().let { catalog ->
        AnimeCatalogFilterCatalog(
            sortOptions = catalog.sortOptions.map { AnimeCatalogFilterOption(it.id, it.title) },
            typeOptions = catalog.typeOptions.map { AnimeCatalogFilterOption(it.id, it.title) },
            statusOptions = catalog.statusOptions.map { AnimeCatalogFilterOption(it.id, it.title) },
            genreOptions = catalog.genreOptions.map { AnimeCatalogFilterOption(it.id, it.title) },
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

    override fun close() {
        sources.clear()
        client.close()
    }
}

private fun org.akkirrai.beakokit.model.AnimeTitle.toSharedAnime(): Anime = Anime(
    id = id,
    title = displayName,
    subtitle = listOfNotNull(type, year?.toString()).joinToString(" \u00B7 "),
    episodesLabel = episodeCount?.let { "$it episodes" } ?: "Episodes unknown",
    status = status ?: "Unknown",
    nextEpisodeAt = nextEpisodeAt,
    posterUrl = posterUrl,
    posterFallbackUrl = posterFallbackUrl,
    description = description,
    genres = genres,
    alternativeTitles = allNames().filterNot { it.equals(displayName, ignoreCase = true) },
    ratings = ratings.map { AnimeRating(it.source, it.value, it.votes) },
    ageRating = ageRating,
    viewCount = viewCount,
    screenshots = screenshots,
    trailer = trailer?.let { AnimeTrailer(it.id, it.site, it.thumbnailUrl, it.sourceUrl) },
    sourceMaterial = sourceMaterial,
    studios = studios,
    releaseDate = year?.toString(),
)
