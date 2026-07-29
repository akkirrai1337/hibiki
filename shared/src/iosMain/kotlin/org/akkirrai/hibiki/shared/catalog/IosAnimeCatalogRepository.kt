package org.akkirrai.hibiki.shared.catalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.source.BuiltInSources
import org.akkirrai.hibiki.shared.model.Anime

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

    override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage {
        val items = source.search(query.text).map(AnimeTitle::toSharedAnime)
        val pageSize = query.pageSize.coerceAtLeast(1)
        val pageItems = items.drop(query.offset).take(pageSize)
        return AnimeCatalogPage(
            items = pageItems,
            page = query.page.coerceAtLeast(1),
            canLoadMore = query.offset + pageItems.size < items.size,
        )
    }

    override suspend fun getDetails(id: String, fallback: Anime): Anime =
        source.getById(id).toSharedAnime()

    fun close() {
        client.close()
    }
}

private fun AnimeTitle.toSharedAnime(): Anime = Anime(
    id = id,
    title = displayName,
    subtitle = listOfNotNull(englishName, originalName.takeUnless { it == displayName })
        .distinct()
        .joinToString(" / "),
    episodesLabel = episodeCount?.let { "$it episodes" }.orEmpty(),
    status = status.orEmpty(),
    nextEpisodeAt = nextEpisodeAt?.times(1_000L),
    posterUrl = posterUrl,
    posterFallbackUrl = posterFallbackUrl,
    description = description,
    genres = genres,
    alternativeTitles = allNames().filterNot { it == displayName },
    ageRating = ageRating,
    viewCount = viewCount,
    studios = studios,
    releaseDate = year?.toString(),
)
