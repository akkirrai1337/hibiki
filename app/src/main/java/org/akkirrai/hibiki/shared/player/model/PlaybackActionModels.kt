package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.model.WatchSource

fun EpisodeWatchProgress.isWatchedToEnd(): Boolean =
    durationMs > 0L && positionMs >= (durationMs - 1_000L).coerceAtLeast(0L)

/** Common request identity retained so a failed resolve can be retried exactly. */
data class PlaybackRequest(
    val episode: WatchEpisode,
    val source: WatchSource,
    val preferredPlayerName: String? = null,
    val preferredQuality: String? = null,
)

/** Actions emitted by the common player settings content to the host orchestration layer. */
sealed interface PlaybackSettingsAction {
    data class SelectVoiceover(val source: WatchSource) : PlaybackSettingsAction
    data class SelectPlayer(val playerName: String?) : PlaybackSettingsAction
    data class SelectQuality(val qualityLabel: String?) : PlaybackSettingsAction
    data class SetAutoSkipSegments(val enabled: Boolean) : PlaybackSettingsAction
    data class SetAutoPlayNextEpisode(val enabled: Boolean) : PlaybackSettingsAction
}

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
