package org.akkirrai.hibiki.shared.player

import org.akkirrai.beakokit.playback.ResolvedPlaybackStream
import org.akkirrai.hibiki.shared.model.PlaybackSegment
import org.akkirrai.hibiki.shared.model.PlaybackStream

fun ResolvedPlaybackStream.toPlaybackStream(
    animeTitle: String,
    sourceTitle: String,
    episodeTitle: String,
): PlaybackStream {
    val quality = validation.quality ?: stream.quality ?: link.quality
    return PlaybackStream(
        animeTitle = animeTitle,
        sourceTitle = sourceTitle,
        episodeTitle = episodeTitle,
        streamUrl = validation.finalUrl,
        streamType = resolvePlaybackStreamType(validation.streamType.name),
        qualityLabel = quality,
        availableQualityLabels = (availableQualityLabels + quality)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .distinct(),
        headers = stream.headers.ifEmpty { link.headers },
        segments = selectPlaybackSegments(
            apiSegments = link.segments.map { segment ->
                PlaybackSegment(
                    type = resolvePlaybackSegmentType(segment.type.name),
                    startMs = segment.startMs,
                    endMs = segment.endMs,
                )
            },
            extractedSegments = stream.segments.map { segment ->
                PlaybackSegment(
                    type = resolvePlaybackSegmentType(segment.type.name),
                    startMs = segment.startMs,
                    endMs = segment.endMs,
                )
            },
        ),
        videoId = link.videoId,
    )
}
