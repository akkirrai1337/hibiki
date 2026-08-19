package org.akkirrai.hibiki.player

import org.akkirrai.hibiki.player.model.WatchEpisode

/** Dispatches episode changes with the same UI/progress ordering on every host. */
fun dispatchPlayerEpisodeSelection(
    episode: WatchEpisode,
    setControlsVisible: () -> Unit,
    pausePlayback: () -> Unit,
    persistProgress: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
) {
    setControlsVisible()
    // Pause the outgoing episode immediately -- it otherwise keeps playing in the background
    // for as long as the next episode's stream takes to load, which looks like the switch
    // silently did nothing.
    pausePlayback()
    persistProgress()
    onEpisodeSelected(episode)
}

fun dispatchAdjacentPlayerEpisodeSelection(
    episodes: List<WatchEpisode>,
    currentEpisodeId: String,
    currentEpisodeNumber: Double?,
    offset: Int,
    setControlsVisible: () -> Unit,
    pausePlayback: () -> Unit,
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
        pausePlayback = pausePlayback,
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
