package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.PlaybackSegment
import org.akkirrai.hibiki.shared.player.model.PlaybackStream

fun buildSkipSegmentKey(episodeId: String, segment: PlaybackSegment): String =
    "$episodeId:${segment.type}:${segment.startMs}:${segment.endMs}"

/**
 * Identity of the platform playback session.
 *
 * Metadata changes must not recreate the native player, while a different
 * transport URL or request headers must release the old native session.
 */
data class PlaybackSessionKey(
    val streamUrl: String,
    val headers: Map<String, String>,
)

fun PlaybackStream.sessionKey(): PlaybackSessionKey = PlaybackSessionKey(
    streamUrl = streamUrl,
    headers = headers,
)

const val WATCH_SOURCE_SEPARATOR = "|watch|"

fun watchTitleIdFromSourceId(sourceId: String): String =
    if (WATCH_SOURCE_SEPARATOR in sourceId) {
        sourceId.substringBefore(WATCH_SOURCE_SEPARATOR)
    } else if ('|' in sourceId) {
        sourceId.substringBefore('|')
    } else {
        sourceId.substringBefore(':')
    }

fun buildWatchSourceId(animeId: String, dubbingTitle: String, index: Int): String {
    val slug = dubbingTitle.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        .trim('-')
        .ifBlank { "voiceover-$index" }
    return "$animeId$WATCH_SOURCE_SEPARATOR$slug-$index"
}
