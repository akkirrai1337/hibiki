package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchEpisode

fun resolveCurrentEpisodeTitle(
    playbackTitle: String?,
    currentEpisodeId: String?,
    episodes: List<WatchEpisode>,
): String {
    val title = playbackTitle.orEmpty().trim()
    if (title.isNotBlank()) return title

    return episodes
        .firstOrNull { it.id == currentEpisodeId }
        ?.let { "Episode ${formatEpisodeNumber(it.number)}" }
        .orEmpty()
}
