package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchEpisode

fun mergeWatchEpisodes(
    primary: List<WatchEpisode>,
    secondary: List<WatchEpisode>,
): List<WatchEpisode> = (primary + secondary)
    .associateBy(WatchEpisode::id)
    .values
    .sortedBy(WatchEpisode::number)
