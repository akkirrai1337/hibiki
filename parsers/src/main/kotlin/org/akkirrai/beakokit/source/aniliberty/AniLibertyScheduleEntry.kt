package org.akkirrai.beakokit.source.aniliberty

import org.akkirrai.beakokit.model.AnimeTitle

data class AniLibertyScheduleEntry(
    val release: AnimeTitle,
    /** ISO day index: 1 (Monday) through 7 (Sunday), if supplied. */
    val dayOfWeek: Int?,
)
