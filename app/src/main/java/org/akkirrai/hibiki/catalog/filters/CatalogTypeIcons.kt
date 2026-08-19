package org.akkirrai.hibiki.catalog.filters

import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.catalog.*
import org.akkirrai.hibiki.catalog.model.AnimeTypeAlias

fun AnimeTypeAlias.iconResource(): Int = when (this) {
    AnimeTypeAlias.Tv -> R.drawable.animite_tv
    AnimeTypeAlias.Ona -> R.drawable.animite_ona
    AnimeTypeAlias.Ova -> R.drawable.animite_ova
    AnimeTypeAlias.Movie -> R.drawable.animite_movie
}
