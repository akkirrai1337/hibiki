package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.player.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.player.model.PlaybackSegmentType
import org.akkirrai.hibiki.shared.player.model.PlaybackStreamType
import org.akkirrai.hibiki.shared.player.model.TitleWatchState
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.model.WatchSource

/** Shows the platform playback surface only while the common shell is on Player. */
fun shouldShowPlaybackHost(
    currentRoute: AppRoute,
    hasPlayback: Boolean,
    hasPendingContext: Boolean,
): Boolean = currentRoute is AppRoute.Player && (hasPlayback || hasPendingContext)

data class PlaybackProgressSnapshot(
    val positionMs: Long,
    val durationMs: Long,
)

/** Keeps progress persistence decisions identical across media transports. */
fun resolvePersistablePlaybackProgress(
    positionMs: Long,
    durationMs: Long,
): PlaybackProgressSnapshot? =
    PlaybackProgressSnapshot(positionMs, durationMs).takeIf { it.positionMs > 0L }

/** Re-enables watch-flow actions when the common route advances to the next screen. */
fun watchNavigationLockKey(
    animeId: String?,
    sourceId: String?,
    isPlayerRoute: Boolean,
): String? = when {
    animeId == null -> null
    isPlayerRoute -> "player:$animeId:${sourceId.orEmpty()}"
    sourceId == null -> "sources:$animeId"
    else -> "episodes:$animeId:$sourceId"
}

fun filterProgressForSource(progressItems: List<EpisodeWatchProgress>, selectedSource: WatchSource?): List<EpisodeWatchProgress> =
    selectedSource?.let { source -> progressItems.filter { it.sourceId == source.sourceId } } ?: progressItems

fun resolveSourceProgress(fallback: TitleWatchState?, progressItems: List<EpisodeWatchProgress>): TitleWatchState? =
    progressItems.maxByOrNull(EpisodeWatchProgress::updatedAt) ?: fallback

fun resolveResumeWatchState(progressItems: List<EpisodeWatchProgress>): TitleWatchState? =
    progressItems
        .asSequence()
        .filter { it.positionMs > 0L && !it.isWatchedToEnd() }
        .maxByOrNull(EpisodeWatchProgress::updatedAt)

fun resolvePlaybackStreamType(rawType: String): PlaybackStreamType = when (rawType) {
    "HLS" -> PlaybackStreamType.HLS
    "MP4" -> PlaybackStreamType.MP4
    "DASH" -> PlaybackStreamType.DASH
    else -> error("Unknown playback stream type: $rawType")
}

fun resolvePlaybackSegmentType(rawType: String): PlaybackSegmentType = when (rawType) {
    "OPENING" -> PlaybackSegmentType.Opening
    "ENDING" -> PlaybackSegmentType.Ending
    "UNKNOWN" -> PlaybackSegmentType.Unknown
    else -> error("Unknown playback segment type: $rawType")
}

/**
 * Returns whether a transport has reached the end of the current episode.
 *
 * The tolerance matches the existing Android progress semantics: a transport
 * may report its final position a fraction before its exact duration.
 */
fun isPlaybackComplete(
    positionMs: Long,
    durationMs: Long,
    toleranceMs: Long = 1_000L,
): Boolean {
    if (durationMs <= 0L || toleranceMs < 0L) return false
    return positionMs >= (durationMs - toleranceMs).coerceAtLeast(0L)
}

/** Resolves the next episode once, using the same completion policy on every host. */
fun resolveAutoPlayNextEpisode(
    episodes: List<WatchEpisode>,
    currentEpisodeId: String,
    currentEpisodeNumber: Double?,
    positionMs: Long,
    durationMs: Long,
    autoPlayEnabled: Boolean,
    completionHandled: Boolean,
): WatchEpisode? {
    if (!autoPlayEnabled || completionHandled || !isPlaybackComplete(positionMs, durationMs)) {
        return null
    }
    return resolveAdjacentEpisode(
        episodes = episodes,
        currentEpisodeId = currentEpisodeId,
        currentEpisodeNumber = currentEpisodeNumber,
        offset = 1,
    )
}
