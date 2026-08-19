package org.akkirrai.hibiki.catalog.filters

import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.catalog.*
import org.akkirrai.hibiki.catalog.model.AnimeStatus

fun AnimeStatus.iconResource(): Int = when (this) {
    AnimeStatus.Finished -> R.drawable.animite_finished
    AnimeStatus.Releasing -> R.drawable.animite_releasing
    AnimeStatus.NotYetReleased -> R.drawable.animite_not_yet_released
    AnimeStatus.Cancelled -> R.drawable.animite_cancelled
    AnimeStatus.Hiatus -> R.drawable.animite_hiatus
}
