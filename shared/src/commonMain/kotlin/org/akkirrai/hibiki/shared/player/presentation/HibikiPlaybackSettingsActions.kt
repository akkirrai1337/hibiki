package org.akkirrai.hibiki.shared.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.player.model.PlaybackRoute
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.WatchDataRepository

private fun org.akkirrai.hibiki.shared.player.model.PlaybackContext.asWatchSource(
    quality: String? = selectedQualityLabel,
) = WatchSource(sourceId, sourceTitle, episodes.size, quality)

private fun org.akkirrai.hibiki.shared.player.model.PlaybackContext.asEpisode() =
    WatchEpisode(episodeId, episodeNumber, null)

internal data class PlaybackSettingsPlaybackRequest(
    val episode: WatchEpisode,
    val source: WatchSource,
    val playerName: String? = null,
    val quality: String? = null,
    val episodes: List<WatchEpisode>,
)

internal fun handlePlaybackSettingsAction(
    action: PlaybackSettingsAction,
    route: PlaybackRoute,
    repository: WatchDataRepository,
    scope: CoroutineScope,
    onSourceSelected: (String, WatchSource) -> Unit,
    onSelectionChanged: (WatchSource, String?, String?) -> Unit,
    onRequestPlayback: (PlaybackSettingsPlaybackRequest) -> Unit,
    onAutoSkipChanged: (Boolean) -> Unit,
    onAutoPlayNextChanged: (Boolean) -> Unit,
) {
    fun persist(source: WatchSource, playerName: String?, quality: String?) {
        onSelectionChanged(source, playerName, quality)
    }
    when (action) {
        is PlaybackSettingsAction.SelectVoiceover -> scope.launch {
            val episodes = runCatching { repository.getEpisodes(action.source.sourceId) }.getOrNull()
                ?: return@launch
            val episode = episodes.firstOrNull { it.number == route.context.episodeNumber }
                ?: episodes.firstOrNull() ?: return@launch
            onSourceSelected(route.context.titleId, action.source)
            persist(action.source, null, action.source.qualityLabel)
            onRequestPlayback(
                PlaybackSettingsPlaybackRequest(episode, action.source, quality = action.source.qualityLabel, episodes = episodes),
            )
        }
        is PlaybackSettingsAction.SelectPlayer -> {
            val source = route.context.asWatchSource()
            persist(source, action.playerName, route.context.selectedQualityLabel)
            onRequestPlayback(PlaybackSettingsPlaybackRequest(route.context.asEpisode(), source, action.playerName, route.context.selectedQualityLabel, route.context.episodes))
        }
        is PlaybackSettingsAction.SelectQuality -> {
            val source = route.context.asWatchSource(action.qualityLabel)
            persist(source, route.context.selectedPlayerName, action.qualityLabel)
            onRequestPlayback(PlaybackSettingsPlaybackRequest(route.context.asEpisode(), source, route.context.selectedPlayerName, action.qualityLabel, route.context.episodes))
        }
        is PlaybackSettingsAction.SetAutoSkipSegments -> onAutoSkipChanged(action.enabled)
        is PlaybackSettingsAction.SetAutoPlayNextEpisode -> onAutoPlayNextChanged(action.enabled)
    }
}
