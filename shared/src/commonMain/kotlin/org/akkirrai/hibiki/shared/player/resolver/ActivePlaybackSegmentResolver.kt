package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.PlaybackSegment

fun resolveActivePlaybackSegment(
    segments: List<PlaybackSegment>?,
    positionMs: Long,
): PlaybackSegment? = segments?.firstOrNull { segment ->
    positionMs >= segment.startMs && positionMs < segment.endMs
}
