package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.WatchEpisode

data class EpisodeNavigationAvailability(
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)

fun resolveEpisodeNavigationAvailability(
    episodes: List<WatchEpisode>,
    currentEpisodeId: String,
    currentEpisodeNumber: Double? = null,
): EpisodeNavigationAvailability {
    val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
        .takeIf { it >= 0 }
        ?: currentEpisodeNumber
            ?.let { number -> episodes.indexOfFirst { it.number == number } }
        ?: -1
    return EpisodeNavigationAvailability(
        hasPrevious = currentIndex > 0,
        hasNext = currentIndex != -1 && currentIndex < episodes.lastIndex,
    )
}
