package org.akkirrai.hibiki.feature.details

import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.RelatedAnime
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.hibiki.shared.details.DetailsHeroInfo

internal data class DetailsUiModel(
    val anime: Anime,
    val hero: DetailsHeroInfo,
    val description: String,
    val sections: List<DetailsSection>,
)

internal sealed interface DetailsSection {
    val key: String
}

internal data class RelatedSection(
    val items: List<RelatedAnime>,
) : DetailsSection {
    override val key: String = "related"
}

internal data class SimilarSection(
    val items: List<RelatedAnime>,
) : DetailsSection {
    override val key: String = "similar"
}

internal fun buildDetailsUiModel(
    anime: Anime,
    hero: DetailsHeroInfo,
    description: String,
    contentFeatures: Set<SourceCapability>,
): DetailsUiModel {
    val relatedItems = if (SourceCapability.RELATED_TITLES in contentFeatures) {
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
    val similarItems = if (SourceCapability.SIMILAR_TITLES in contentFeatures) {
        anime.similarAnime
            .filterNot { it.id == anime.id || it.id in relatedIds }
            .distinctBy(RelatedAnime::id)
    } else {
        emptyList()
    }
    val sections = buildList {
        if (relatedItems.isNotEmpty()) {
            add(
                RelatedSection(items = relatedItems)
            )
        }
        if (similarItems.isNotEmpty()) {
            add(
                SimilarSection(items = similarItems)
            )
        }
    }

    return DetailsUiModel(
        anime = anime,
        hero = hero,
        description = description,
        sections = sections,
    )
}

private fun Anime.toRelatedAnime(): RelatedAnime = RelatedAnime(
    id = id,
    title = title,
    posterUrl = posterUrl,
    posterFallbackUrl = posterFallbackUrl,
    status = status,
)
