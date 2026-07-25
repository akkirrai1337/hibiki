package org.akkirrai.hibiki.shared.home

import org.akkirrai.hibiki.shared.model.Anime

fun mergeAnimePreservingOrder(primary: List<Anime>, additions: List<Anime>): List<Anime> =
    (primary + additions).distinctBy(Anime::id)
