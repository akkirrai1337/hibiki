package org.akkirrai.hibiki.core.anilist

import org.akkirrai.hibiki.catalog.model.AniListCharacter
import org.akkirrai.hibiki.catalog.model.AniListEnrichment

internal fun AniListMediaDetail.toEnrichment(anilistId: Int): AniListEnrichment = AniListEnrichment(
    anilistId = anilistId,
    bannerUrl = bannerImage,
    averageScore = averageScore,
    characters = characters?.edges.orEmpty().mapNotNull { edge -> edge.toCharacterOrNull() },
    directors = staff?.edges.orEmpty()
        .filter { edge -> edge.role?.contains("director", ignoreCase = true) == true }
        .mapNotNull { edge -> edge.node?.name?.full?.takeIf(String::isNotBlank) }
        .distinct(),
)

private fun AniListCharacterEdge.toCharacterOrNull(): AniListCharacter? {
    val characterNode = node ?: return null
    val name = characterNode.name?.full?.takeIf(String::isNotBlank)
        ?: characterNode.name?.native?.takeIf(String::isNotBlank)
        ?: return null
    val voiceActor = voiceActors.firstOrNull()
    return AniListCharacter(
        name = name,
        imageUrl = characterNode.image?.medium,
        role = role ?: "SUPPORTING",
        voiceActorName = voiceActor?.name?.full?.takeIf(String::isNotBlank),
        voiceActorImageUrl = voiceActor?.image?.medium,
    )
}
