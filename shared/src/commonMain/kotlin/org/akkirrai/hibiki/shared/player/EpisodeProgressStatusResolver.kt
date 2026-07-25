package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.EpisodeProgressStatus
import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.model.progressStatus

fun resolveEpisodeProgressStatus(progress: EpisodeWatchProgress?): EpisodeProgressStatus =
    progress?.progressStatus() ?: EpisodeProgressStatus.NotStarted
