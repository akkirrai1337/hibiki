package org.akkirrai.hibiki.shared.catalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.RelatedAnimeTitle
import org.akkirrai.beakokit.source.BuiltInSources
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilter
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.shared.model.AnimeRating
import org.akkirrai.hibiki.shared.model.AnimeTrailer
import org.akkirrai.hibiki.shared.model.RelatedAnime

internal class IosAnimeCatalogRepository : AnimeCatalogRepository {
    private val client = HttpClient(Darwin)
    private val source = BuiltInSources.catalog.create(
        BuiltInSources.ANI_LIBERTY_ID,
        DefaultSourceContext(
            httpClient = client,
            preferredLanguages = listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
        ),
    )

    override val initialItems: List<Anime> = emptyList()

    override suspend fun filterCatalog(): AnimeCatalogFilterCatalog =
        source.getSearchFilterCatalog().let { catalog ->
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

    override suspend fun getDetails(id: String, fallback: Anime): Anime =
        source.getById(id).toSharedAnime(fallback)

    override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage {
        val filters = query.filters
        val titles = source.search(
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
        val items = titles.map(AnimeTitle::toSharedAnime)
        return AnimeCatalogPage(
            items = items,
            page = query.page.coerceAtLeast(1),
            canLoadMore = items.size >= query.pageSize.coerceAtLeast(1),
        )
    }

    fun close() {
        client.close()
    }
}

private fun AnimeTitle.toSharedAnime(fallback: Anime? = null): Anime {
    val resolvedStatus = status ?: fallback?.status ?: "Unknown"
    val resolvedEpisodesLabel = when (releaseStatus) {
        AnimeReleaseStatus.ANNOUNCEMENT -> "announcement"
        AnimeReleaseStatus.RELEASED -> (availableEpisodeCount ?: episodeCount)?.let { "$it episodes" }
        else -> availableEpisodeCount?.let { "$it episodes" }
    } ?: fallback?.episodesLabel ?: "Episodes unknown"

    return Anime(
    id = id,
    title = displayName,
    subtitle = listOfNotNull(type, year?.toString()).joinToString(" · "),
    episodesLabel = resolvedEpisodesLabel,
    status = resolvedStatus,
    nextEpisodeAt = nextEpisodeAt ?: fallback?.nextEpisodeAt,
    posterUrl = posterUrl ?: fallback?.posterUrl,
    posterFallbackUrl = posterFallbackUrl
        ?.takeIf { it.isNotBlank() && it != posterUrl }
        ?: fallback?.posterFallbackUrl?.takeIf { it.isNotBlank() && it != posterUrl },
    description = description ?: fallback?.description,
    genres = genres.ifEmpty { fallback?.genres.orEmpty() },
    alternativeTitles = allNames().filterNot { it.equals(displayName, ignoreCase = true) },
    ratings = ratings.map { AnimeRating(it.source, it.value, it.votes) }.ifEmpty { fallback?.ratings.orEmpty() },
    ageRating = ageRating ?: fallback?.ageRating,
    viewCount = viewCount ?: fallback?.viewCount,
    screenshots = screenshots.ifEmpty { fallback?.screenshots.orEmpty() },
    trailer = trailer?.let { AnimeTrailer(it.id, it.site, it.thumbnailUrl, it.sourceUrl) } ?: fallback?.trailer,
    sourceMaterial = sourceMaterial ?: fallback?.sourceMaterial,
    studios = studios.ifEmpty { fallback?.studios.orEmpty() },
    similarAnime = similarAnime.map { it.toSharedRelatedAnime() }.ifEmpty { fallback?.similarAnime.orEmpty() },
    franchiseAnime = franchiseAnime.map { it.toSharedRelatedAnime() }.ifEmpty { fallback?.franchiseAnime.orEmpty() },
    relatedAnime = relatedAnime.map { it.toSharedRelatedAnime() }.ifEmpty { fallback?.relatedAnime.orEmpty() },
    releaseDate = year?.toString() ?: fallback?.releaseDate,
    )
}

private fun RelatedAnimeTitle.toSharedRelatedAnime(): RelatedAnime = RelatedAnime(
    id = id,
    title = title,
    posterUrl = posterUrl,
    type = type,
    year = year,
    episodeCount = episodeCount,
    status = status,
)
