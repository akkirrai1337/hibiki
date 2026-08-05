package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.EpisodeWatchProgress

fun EpisodeWatchProgress.isWatchedToEnd(): Boolean =
    durationMs > 0L && positionMs >= (durationMs - 1_000L).coerceAtLeast(0L)
