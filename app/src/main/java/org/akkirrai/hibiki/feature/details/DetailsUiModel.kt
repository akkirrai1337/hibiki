package org.akkirrai.hibiki.feature.details

import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.shared.details.DetailsHeroInfo
import org.akkirrai.hibiki.shared.details.buildDetailsUiModel as buildSharedDetailsUiModel

internal typealias DetailsUiModel = org.akkirrai.hibiki.shared.details.DetailsUiModel
internal typealias DetailsSection = org.akkirrai.hibiki.shared.details.DetailsSection
internal typealias RelatedSection = org.akkirrai.hibiki.shared.details.RelatedSection
internal typealias SimilarSection = org.akkirrai.hibiki.shared.details.SimilarSection

internal fun buildDetailsUiModel(
    anime: Anime,
    hero: DetailsHeroInfo,
    description: String,
    contentFeatures: Set<SourceCapability>,
): DetailsUiModel = buildSharedDetailsUiModel(
    anime = anime,
    hero = hero,
    description = description,
    includeRelated = SourceCapability.RELATED_TITLES in contentFeatures,
    includeSimilar = SourceCapability.SIMILAR_TITLES in contentFeatures,
)
