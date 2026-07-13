package org.akkirrai.hibiki.feature.details

import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.RelatedAnime

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

internal fun buildDetailsUiModel(
    anime: Anime,
    hero: HeroInfo,
    description: String,
): DetailsUiModel {
    val sections = buildList {
        if (anime.franchiseAnime.isNotEmpty()) {
            add(
                RelatedSection(items = anime.franchiseAnime)
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
