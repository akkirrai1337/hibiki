package org.akkirrai.hibiki.shared.details.model

import org.akkirrai.hibiki.shared.catalog.model.Anime

fun resolveAnimeDescription(anime: Anime): String = anime.description?.takeIf(String::isNotBlank).orEmpty()
