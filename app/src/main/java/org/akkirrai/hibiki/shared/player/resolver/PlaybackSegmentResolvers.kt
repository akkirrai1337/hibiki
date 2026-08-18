package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.PlaybackSegment
import org.akkirrai.hibiki.shared.player.model.PlaybackSegmentType

fun resolveActivePlaybackSegment(
    segments: List<PlaybackSegment>?,
    positionMs: Long,
): PlaybackSegment? = segments?.firstOrNull { segment ->
    positionMs >= segment.startMs && positionMs < segment.endMs
}

fun selectPlaybackSegments(
    apiSegments: List<PlaybackSegment>,
    extractedSegments: List<PlaybackSegment>,
): List<PlaybackSegment> {
    val preferred = apiSegments.ifEmpty { extractedSegments }
    return preferred
        .filter { it.endMs > it.startMs }
        .filter { it.startMs >= 0L }
        .filterNot { it.startMs == 0L && it.type != PlaybackSegmentType.Unknown }
}
