package org.akkirrai.hibiki.shared.details.screen

import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.AnimeTrailer

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
