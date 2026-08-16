package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.EpisodeProgressStatus
import org.akkirrai.hibiki.shared.player.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.player.model.progressStatus

fun resolveEpisodeProgressStatus(progress: EpisodeWatchProgress?): EpisodeProgressStatus =
    progress?.progressStatus() ?: EpisodeProgressStatus.NotStarted
