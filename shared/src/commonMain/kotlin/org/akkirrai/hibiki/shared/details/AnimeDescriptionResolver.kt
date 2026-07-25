package org.akkirrai.hibiki.shared.details

import org.akkirrai.hibiki.shared.model.Anime

fun resolveAnimeDescription(anime: Anime): String = anime.description?.takeIf(String::isNotBlank).orEmpty()
