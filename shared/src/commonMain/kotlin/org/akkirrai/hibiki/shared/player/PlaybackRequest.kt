package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.model.WatchSource

/** Common request identity retained so a failed resolve can be retried exactly. */
data class PlaybackRequest(
    val episode: WatchEpisode,
    val source: WatchSource,
    val preferredPlayerName: String? = null,
    val preferredQuality: String? = null,
)
