package org.akkirrai.hibiki.feature.details

import org.akkirrai.hibiki.core.model.Anime

internal data class DetailsUiModel(
    val anime: Anime,
    val hero: HeroInfo,
    val description: String,
)

internal fun buildDetailsUiModel(
    anime: Anime,
    hero: HeroInfo,
    description: String,
): DetailsUiModel = DetailsUiModel(
    anime = anime,
    hero = hero,
    description = description,
)
