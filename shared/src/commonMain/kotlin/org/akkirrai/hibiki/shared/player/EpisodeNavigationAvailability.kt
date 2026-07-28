package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchEpisode

data class EpisodeNavigationAvailability(
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)

fun resolveEpisodeNavigationAvailability(
    episodes: List<WatchEpisode>,
    currentEpisodeId: String,
): EpisodeNavigationAvailability {
    val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
    return EpisodeNavigationAvailability(
        hasPrevious = currentIndex > 0,
        hasNext = currentIndex != -1 && currentIndex < episodes.lastIndex,
    )
}
