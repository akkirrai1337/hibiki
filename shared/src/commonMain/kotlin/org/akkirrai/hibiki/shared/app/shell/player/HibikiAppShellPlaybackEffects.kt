package org.akkirrai.hibiki.shared.app.shell.player

import org.akkirrai.hibiki.shared.navigation.WatchFlowBackEffect
import org.akkirrai.hibiki.shared.player.EpisodesPresenter
import org.akkirrai.hibiki.shared.player.PlayerPresenter
import org.akkirrai.hibiki.shared.app.shell.player.watch.applyHibikiWatchFlowBackEffect
import org.akkirrai.hibiki.shared.app.shell.player.watch.resetHibikiPlayerState

internal class HibikiAppShellPlaybackEffects(
    private val episodesPresenter: EpisodesPresenter,
    private val playerPresenter: PlayerPresenter,
    private val invalidateEpisodes: () -> Unit,
) {
    fun resetPlayerState() {
        resetHibikiPlayerState(playerPresenter)
    }

    fun applyWatchFlowBackEffect(
        effect: WatchFlowBackEffect,
        invalidateEpisodesState: Boolean = true,
    ) {
        applyHibikiWatchFlowBackEffect(
            effect = effect,
            invalidateEpisodes = invalidateEpisodesState,
            onEpisodesInvalidated = invalidateEpisodes,
            episodesPresenter = episodesPresenter,
            playerPresenter = playerPresenter,
        )
    }
}
