package org.akkirrai.hibiki.feature.details

import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.RelatedAnime
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.hibiki.shared.details.DetailsHeroInfo
import org.akkirrai.hibiki.shared.details.buildDetailsRelatedContent

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
    val relatedContent = buildDetailsRelatedContent(
        anime = anime,
        includeRelated = SourceCapability.RELATED_TITLES in contentFeatures,
        includeSimilar = SourceCapability.SIMILAR_TITLES in contentFeatures,
    )
    val relatedItems = relatedContent.relatedItems
    val similarItems = relatedContent.similarItems
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
