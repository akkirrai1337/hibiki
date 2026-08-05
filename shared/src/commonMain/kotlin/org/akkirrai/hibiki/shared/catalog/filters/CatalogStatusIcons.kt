package org.akkirrai.hibiki.shared.catalog.filters

import org.akkirrai.hibiki.shared.catalog.*
import org.akkirrai.hibiki.shared.catalog.model.AnimeStatus

import hibiki.shared.generated.resources.Res
import hibiki.shared.generated.resources.animite_cancelled
import hibiki.shared.generated.resources.animite_finished
import hibiki.shared.generated.resources.animite_hiatus
import hibiki.shared.generated.resources.animite_not_yet_released
import hibiki.shared.generated.resources.animite_releasing
import org.jetbrains.compose.resources.DrawableResource

fun AnimeStatus.iconResource(): DrawableResource = when (this) {
    AnimeStatus.Finished -> Res.drawable.animite_finished
    AnimeStatus.Releasing -> Res.drawable.animite_releasing
    AnimeStatus.NotYetReleased -> Res.drawable.animite_not_yet_released
    AnimeStatus.Cancelled -> Res.drawable.animite_cancelled
    AnimeStatus.Hiatus -> Res.drawable.animite_hiatus
}
