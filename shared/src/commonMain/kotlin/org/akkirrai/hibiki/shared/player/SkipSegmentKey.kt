package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.PlaybackSegment

fun buildSkipSegmentKey(episodeId: String, segment: PlaybackSegment): String =
    "$episodeId:${segment.type}:${segment.startMs}:${segment.endMs}"
