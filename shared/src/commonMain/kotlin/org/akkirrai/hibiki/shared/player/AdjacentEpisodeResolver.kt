package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchEpisode

fun resolveAdjacentEpisode(
    episodes: List<WatchEpisode>,
    currentEpisodeId: String,
    currentEpisodeNumber: Double?,
    offset: Int,
): WatchEpisode? {
    val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
    if (currentIndex != -1) return episodes.getOrNull(currentIndex + offset)

    val number = currentEpisodeNumber ?: return null
    return if (offset < 0) {
        episodes.filter { it.number < number }.maxByOrNull(WatchEpisode::number)
    } else {
        episodes.filter { it.number > number }.minByOrNull(WatchEpisode::number)
    }
}
