package org.akkirrai.hibiki.details.state

import org.akkirrai.hibiki.details.screen.DetailsHeroInfo
import org.akkirrai.hibiki.details.screen.buildDetailsRelatedContent
import org.akkirrai.hibiki.details.model.isAnnouncementStatus

import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.RelatedAnime
import org.akkirrai.hibiki.core.source.AppSourceDescriptor

fun resolveDetailsPlaybackAvailability(
    supportsPlayback: Boolean,
    status: String,
    episodesLabel: String,
): Boolean = supportsPlayback && !isAnnouncementStatus(status, episodesLabel)

fun resolveDetailsPlaybackAvailability(
    watchRepositoryAvailable: Boolean,
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    status: String,
    episodesLabel: String,
): Boolean {
    val supportsPlayback = sources
        .firstOrNull { it.id == selectedSourceId }
        ?.supportsPlayback
        ?: false
    return watchRepositoryAvailable && resolveDetailsPlaybackAvailability(
        supportsPlayback = supportsPlayback,
        status = status,
        episodesLabel = episodesLabel,
    )
}

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
