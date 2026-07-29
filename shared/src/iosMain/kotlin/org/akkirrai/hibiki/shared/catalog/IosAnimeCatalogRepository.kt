package org.akkirrai.hibiki.shared.catalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.akkirrai.beakokit.http.BeakoKitHttpPolicy
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.MapSourceConfig
import org.akkirrai.beakokit.api.SourceConfig
import org.akkirrai.beakokit.api.SourceHealthReporter
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.RelatedAnimeTitle
import org.akkirrai.beakokit.source.BuiltInSources
import org.akkirrai.beakokit.source.yummy.YummyAnimeConfig
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilter
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.shared.model.AnimeRating
import org.akkirrai.hibiki.shared.model.AnimeTrailer
import org.akkirrai.hibiki.shared.model.RelatedAnime
import org.akkirrai.hibiki.shared.source.resolveAnimeSubtitle
import org.akkirrai.hibiki.shared.source.formatReleaseDateLabel
import org.akkirrai.hibiki.shared.source.resolveEpisodesLabel
import org.akkirrai.hibiki.shared.source.resolveAlternativeTitles
import org.akkirrai.hibiki.shared.source.resolveReleaseStatusLabel

internal class IosAnimeCatalogRepository(
    private val preferEnglish: Boolean = false,
    private val sourceId: org.akkirrai.beakokit.api.SourceId = BuiltInSources.ANI_LIBERTY_ID,
    private val sourceHealthReporter: SourceHealthReporter = SourceHealthReporter.NONE,
) : AnimeCatalogRepository {
    private val client = HttpClient(Darwin) {
        installBeakoKitHttpDefaults(
            BeakoKitHttpPolicy(userAgent = "Hibiki/0.1 iOS"),
        )
    }
    private val source = BuiltInSources.catalog.create(
        sourceId,
        DefaultSourceContext(
            httpClient = client,
            config = sourceConfig(sourceId),
            sourceHealthReporter = sourceHealthReporter,
            preferredLanguages = if (preferEnglish) {
                listOf(SourceLanguage.ENGLISH, SourceLanguage.RUSSIAN)
            } else {
                listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH)
            },
        ),
    )

    override val initialItems: List<Anime> = emptyList()

    private fun sourceConfig(sourceId: org.akkirrai.beakokit.api.SourceId): SourceConfig = when (sourceId) {
        BuiltInSources.YUMMY_ANIME_ID -> MapSourceConfig(
            secrets = mapOf(YummyAnimeConfig.APPLICATION_TOKEN to DEFAULT_YUMMY_APPLICATION_TOKEN),
        )
        else -> SourceConfig.EMPTY
    }

    private companion object {
        const val DEFAULT_YUMMY_APPLICATION_TOKEN = "wawegr8j13it4rdw"
    }

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
        source.getById(id).toSharedAnime(preferEnglish, fallback)

    override suspend fun latest(limit: Int): List<Anime> =
        (source as? LatestSource)?.latest(limit.coerceAtLeast(1))
            ?.map { it.toSharedAnime(preferEnglish) }
            .orEmpty()

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
        val items = titles.map { it.toSharedAnime(preferEnglish) }
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

private fun AnimeTitle.toSharedAnime(preferEnglish: Boolean, fallback: Anime? = null): Anime {
    val resolvedPosterUrl = posterUrl ?: fallback?.posterUrl
    val resolvedStatus = if (releaseStatus == AnimeReleaseStatus.UNKNOWN) {
        fallback?.status ?: resolveReleaseStatusLabel(releaseStatus.name, preferEnglish)
    } else {
        resolveReleaseStatusLabel(releaseStatus.name, preferEnglish)
    }
    val resolvedEpisodesLabel = when (releaseStatus) {
        AnimeReleaseStatus.ANNOUNCEMENT -> if (preferEnglish) "announcement" else "\u0430\u043dо\u043d\u0441"
        else -> resolveEpisodesLabel(
            releasedCount = availableEpisodeCount
                ?: episodeCount.takeIf { releaseStatus == AnimeReleaseStatus.RELEASED },
            fallbackLabel = fallback?.episodesLabel,
            preferEnglish = preferEnglish,
        )
    }

    return Anime(
    id = id,
    title = displayName,
    subtitle = resolveAnimeSubtitle(type, year, fallback?.subtitle),
    episodesLabel = resolvedEpisodesLabel,
    status = resolvedStatus,
    nextEpisodeAt = nextEpisodeAt ?: fallback?.nextEpisodeAt,
        posterUrl = resolvedPosterUrl,
        posterFallbackUrl = posterFallbackUrl
        ?.takeIf { it.isNotBlank() && it != resolvedPosterUrl }
        ?: fallback?.posterFallbackUrl?.takeIf { it.isNotBlank() && it != resolvedPosterUrl },
        description = description ?: fallback?.description,
        genres = genres.ifEmpty { fallback?.genres.orEmpty() },
    alternativeTitles = resolveAlternativeTitles(
        primaryTitle = displayName,
        titleCandidates = listOf(russianName, englishName, originalName, japaneseName) + synonyms,
        fallbackTitles = fallback?.alternativeTitles.orEmpty(),
    ),
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
    releaseDate = formatReleaseDateLabel(year, season, preferEnglish) ?: fallback?.releaseDate,
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
