package org.akkirrai.hibiki.shared.model

fun RelatedAnime.toAnime(): Anime = Anime(
    id = id,
    title = title,
    subtitle = "",
    episodesLabel = "",
    status = status.orEmpty(),
    posterUrl = posterUrl,
    posterFallbackUrl = posterFallbackUrl,
)
