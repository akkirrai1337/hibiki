package org.akkirrai.hibiki.shared.app.destination.actions

import org.akkirrai.hibiki.shared.player.model.TitleWatchState
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.model.WatchSource

internal data class AppDestinationWatchActions(
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onSourceClick: (WatchSource) -> Unit,
    val onEpisodeClick: (WatchEpisode) -> Unit,
    val onResumePlayback: (TitleWatchState) -> Unit,
)
