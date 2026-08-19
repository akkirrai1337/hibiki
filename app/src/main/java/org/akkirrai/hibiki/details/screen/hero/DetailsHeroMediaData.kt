package org.akkirrai.hibiki.details.screen

import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.AnimeTrailer

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
