package org.akkirrai.hibiki.shared.details.screen

import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.RelatedAnime

data class DetailsRelatedContent(
    val relatedItems: List<RelatedAnime>,
    val similarItems: List<RelatedAnime>,
)

fun buildDetailsRelatedContent(
    anime: Anime,
    includeRelated: Boolean,
    includeSimilar: Boolean,
): DetailsRelatedContent {
    val relatedItems = if (includeRelated) {
        val sourceRelatedItems = (anime.franchiseAnime + anime.relatedAnime)
            .distinctBy(RelatedAnime::id)
        if (sourceRelatedItems.isNotEmpty() && sourceRelatedItems.none { it.id == anime.id }) {
            listOf(anime.toRelatedAnime()) + sourceRelatedItems
        } else {
            sourceRelatedItems
        }
    } else {
        emptyList()
    }
    val relatedIds = relatedItems.mapTo(mutableSetOf(), RelatedAnime::id)
    val similarItems = if (includeSimilar) {
        anime.similarAnime
            .filterNot { it.id == anime.id || it.id in relatedIds }
            .distinctBy(RelatedAnime::id)
    } else {
        emptyList()
    }
    return DetailsRelatedContent(relatedItems, similarItems)
}

private fun Anime.toRelatedAnime(): RelatedAnime = RelatedAnime(
    id = id,
    title = title,
    posterUrl = posterUrl,
    posterFallbackUrl = posterFallbackUrl,
    status = status,
)
