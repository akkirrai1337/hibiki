package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.EpisodeProgressStatus
import org.akkirrai.hibiki.shared.player.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.model.progressStatus

fun formatEpisodeNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()

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

fun resolveEpisodeProgressStatus(progress: EpisodeWatchProgress?): EpisodeProgressStatus =
    progress?.progressStatus() ?: EpisodeProgressStatus.NotStarted
