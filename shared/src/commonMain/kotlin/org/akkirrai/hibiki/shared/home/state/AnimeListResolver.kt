package org.akkirrai.hibiki.shared.home.state

import org.akkirrai.hibiki.shared.catalog.model.Anime

fun mergeAnimePreservingOrder(primary: List<Anime>, additions: List<Anime>): List<Anime> =
    (primary + additions).distinctBy(Anime::id)
