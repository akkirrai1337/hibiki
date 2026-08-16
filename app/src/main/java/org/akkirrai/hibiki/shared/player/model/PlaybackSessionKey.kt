package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.PlaybackStream

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
