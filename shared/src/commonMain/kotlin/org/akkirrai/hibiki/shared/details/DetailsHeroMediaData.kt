package org.akkirrai.hibiki.shared.details

import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeTrailer

data class DetailsHeroMediaData(
    val trailer: AnimeTrailer?,
)

fun resolveDetailsHeroMediaData(
    anime: Anime,
): DetailsHeroMediaData {
    return DetailsHeroMediaData(
        trailer = anime.trailer?.takeIf { it.playbackUrl != null },
    )
}
