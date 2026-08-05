package org.akkirrai.hibiki.shared.catalog.filters

import org.akkirrai.hibiki.shared.catalog.*
import org.akkirrai.hibiki.shared.catalog.model.AnimeTypeAlias

import hibiki.shared.generated.resources.Res
import hibiki.shared.generated.resources.animite_movie
import hibiki.shared.generated.resources.animite_ona
import hibiki.shared.generated.resources.animite_ova
import hibiki.shared.generated.resources.animite_tv
import org.jetbrains.compose.resources.DrawableResource

fun AnimeTypeAlias.iconResource(): DrawableResource = when (this) {
    AnimeTypeAlias.Tv -> Res.drawable.animite_tv
    AnimeTypeAlias.Ona -> Res.drawable.animite_ona
    AnimeTypeAlias.Ova -> Res.drawable.animite_ova
    AnimeTypeAlias.Movie -> Res.drawable.animite_movie
}
