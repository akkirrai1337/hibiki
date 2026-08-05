package org.akkirrai.hibiki.shared.app.shell.player.watch

import org.akkirrai.hibiki.shared.navigation.WatchFlowBackEffect
import org.akkirrai.hibiki.shared.player.EpisodesPresenter
import org.akkirrai.hibiki.shared.player.EpisodesScreenState
import org.akkirrai.hibiki.shared.player.PlayerPresenter
import org.akkirrai.hibiki.shared.player.resetForNavigation

internal fun resetHibikiPlayerState(playerPresenter: PlayerPresenter) {
    playerPresenter.setState(playerPresenter.state.value.resetForNavigation())
}

internal fun applyHibikiWatchFlowBackEffect(
    effect: WatchFlowBackEffect,
    invalidateEpisodes: Boolean,
    onEpisodesInvalidated: () -> Unit,
    episodesPresenter: EpisodesPresenter,
    playerPresenter: PlayerPresenter,
) {
    when (effect) {
        WatchFlowBackEffect.ResetEpisodesAndPlayer -> {
            if (invalidateEpisodes) onEpisodesInvalidated()
            episodesPresenter.setState(EpisodesScreenState())
            resetHibikiPlayerState(playerPresenter)
        }
        WatchFlowBackEffect.ResetPlayer,
        WatchFlowBackEffect.CloseDetails,
        WatchFlowBackEffect.None,
        -> resetHibikiPlayerState(playerPresenter)
    }
}
