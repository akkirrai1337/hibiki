package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchEpisode

fun resolveCurrentEpisode(
    requestedEpisodeId: String,
    requestedEpisodeNumber: Double?,
    episodes: List<WatchEpisode>,
    currentEpisodes: List<WatchEpisode>,
    savedEpisodeNumber: Double? = null,
): WatchEpisode? {
    episodes.firstOrNull { it.id == requestedEpisodeId }?.let { return it }
    val knownNumber = requestedEpisodeNumber
        ?: currentEpisodes.firstOrNull { it.id == requestedEpisodeId }?.number
        ?: savedEpisodeNumber
    return knownNumber?.let { number -> episodes.firstOrNull { it.number == number } }
}
