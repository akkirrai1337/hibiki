package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.PlaybackSegmentType
import org.akkirrai.hibiki.shared.model.PlaybackStreamType

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
