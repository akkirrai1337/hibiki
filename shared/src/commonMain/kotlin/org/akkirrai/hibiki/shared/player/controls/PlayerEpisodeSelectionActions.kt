package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.WatchEpisode

/** Dispatches episode changes with the same UI/progress ordering on every host. */
fun dispatchPlayerEpisodeSelection(
    episode: WatchEpisode,
    setControlsVisible: () -> Unit,
    persistProgress: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
) {
    setControlsVisible()
    persistProgress()
    onEpisodeSelected(episode)
}

fun dispatchAdjacentPlayerEpisodeSelection(
    episodes: List<WatchEpisode>,
    currentEpisodeId: String,
    currentEpisodeNumber: Double?,
    offset: Int,
    setControlsVisible: () -> Unit,
    persistProgress: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
): Boolean {
    val episode = resolveAdjacentEpisode(
        episodes = episodes,
        currentEpisodeId = currentEpisodeId,
        currentEpisodeNumber = currentEpisodeNumber,
        offset = offset,
    ) ?: return false
    dispatchPlayerEpisodeSelection(
        episode = episode,
        setControlsVisible = setControlsVisible,
        persistProgress = persistProgress,
        onEpisodeSelected = onEpisodeSelected,
    )
    return true
}

fun dispatchPlayerClose(
    persistProgress: () -> Unit,
    onBack: () -> Unit,
) {
    persistProgress()
    onBack()
}

fun dispatchPlayerSettingsAction(
    action: PlaybackSettingsAction,
    setControlsVisible: () -> Unit,
    persistProgress: () -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
) {
    setControlsVisible()
    persistProgress()
    onSettingsAction(action)
}

fun dispatchPlayerSettingsSelection(
    action: PlaybackSettingsAction,
    setControlsVisible: () -> Unit,
    persistProgress: () -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
) {
    dispatchPlayerSettingsAction(
        action = action,
        setControlsVisible = setControlsVisible,
        persistProgress = persistProgress,
        onSettingsAction = onSettingsAction,
    )
}
