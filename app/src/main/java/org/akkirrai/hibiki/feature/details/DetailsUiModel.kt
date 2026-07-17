package org.akkirrai.hibiki.feature.details

import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.RelatedAnime
import org.akkirrai.hibiki.core.source.AnimeSourceContentFeature

internal data class DetailsUiModel(
    val anime: Anime,
    val hero: HeroInfo,
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
    hero: HeroInfo,
    description: String,
    contentFeatures: Set<AnimeSourceContentFeature>,
): DetailsUiModel {
    val relatedItems = if (AnimeSourceContentFeature.RELATED_TITLES in contentFeatures) {
        (anime.franchiseAnime + anime.relatedAnime)
            .filterNot { it.id == anime.id }
            .distinctBy(RelatedAnime::id)
    } else {
        emptyList()
    }
    val relatedIds = relatedItems.mapTo(mutableSetOf(), RelatedAnime::id)
    val similarItems = if (AnimeSourceContentFeature.SIMILAR_TITLES in contentFeatures) {
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
