package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.PlaybackSegment
import org.akkirrai.hibiki.shared.model.PlaybackSegmentType

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
