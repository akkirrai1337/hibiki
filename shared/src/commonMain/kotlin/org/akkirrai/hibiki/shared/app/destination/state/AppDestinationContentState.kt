package org.akkirrai.hibiki.shared.app.destination.state

import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.player.model.TitleWatchState
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.player.EpisodesScreenState
import org.akkirrai.hibiki.shared.player.WatchSourcesScreenState

internal data class AppDestinationContentState(
    val selectedAnime: Anime?,
    val watchAnime: Anime?,
    val watchState: WatchSourcesScreenState,
    val episodesState: EpisodesScreenState,
    val selectedWatchSource: WatchSource?,
    val playbackError: String?,
    val playbackLoading: Boolean,
    val watchRepositoryAvailable: Boolean,
    val isDetailsLoading: Boolean,
    val detailsError: String?,
    val detailsResumeState: TitleWatchState?,
    val isPlayerRoute: Boolean,
    val playbackHostAvailable: Boolean,
    val currentRoute: AppRoute?,
)
