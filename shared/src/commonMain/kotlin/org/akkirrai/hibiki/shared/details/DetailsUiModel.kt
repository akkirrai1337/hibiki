package org.akkirrai.hibiki.shared.details

import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.RelatedAnime

data class DetailsUiModel(
    val anime: Anime,
    val hero: DetailsHeroInfo,
    val description: String,
    val sections: List<DetailsSection>,
)

sealed interface DetailsSection {
    val key: String
}

data class RelatedSection(
    val items: List<RelatedAnime>,
) : DetailsSection {
    override val key: String = "related"
}

data class SimilarSection(
    val items: List<RelatedAnime>,
) : DetailsSection {
    override val key: String = "similar"
}

fun buildDetailsUiModel(
    anime: Anime,
    hero: DetailsHeroInfo,
    description: String,
    includeRelated: Boolean,
    includeSimilar: Boolean,
): DetailsUiModel {
    val relatedContent = buildDetailsRelatedContent(
        anime = anime,
        includeRelated = includeRelated,
        includeSimilar = includeSimilar,
    )
    val sections = buildList {
        if (relatedContent.relatedItems.isNotEmpty()) {
            add(RelatedSection(items = relatedContent.relatedItems))
        }
        if (relatedContent.similarItems.isNotEmpty()) {
            add(SimilarSection(items = relatedContent.similarItems))
        }
    }

    return DetailsUiModel(
        anime = anime,
        hero = hero,
        description = description,
        sections = sections,
    )
}
