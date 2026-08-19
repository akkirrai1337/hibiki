package org.akkirrai.hibiki.player

import org.akkirrai.hibiki.player.model.PlaybackContext
import org.akkirrai.hibiki.player.model.PlaybackSelection
import org.akkirrai.hibiki.player.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.player.resolvePlaybackPreferences

internal fun createPlaybackSelection(
    titleId: String,
    source: WatchSource,
    quality: String?,
    playerName: String?,
): PlaybackSelection = PlaybackSelection(
    titleId = titleId,
    sourceId = source.sourceId,
    sourceTitle = source.title,
    quality = quality,
    playerName = playerName,
)

internal data class PreparedPlaybackRequest(
    val source: WatchSource,
    val playerName: String?,
    val quality: String?,
    val episodes: List<WatchEpisode>,
    val context: PlaybackContext,
)

internal fun preparePlaybackRequest(
    titleId: String,
    animeTitle: String = "",
    episode: WatchEpisode,
    selectedSource: WatchSource?,
    sourceOverride: WatchSource?,
    preferredPlayerName: String?,
    preferredQuality: String?,
    savedSelection: PlaybackSelection?,
    allowSavedSelection: Boolean,
    episodes: List<WatchEpisode>,
    settingsOptions: PlaybackSettingsOptions,
): PreparedPlaybackRequest? {
    val source = sourceOverride ?: selectedSource ?: return null
    val preferences = resolvePlaybackPreferences(
        sourceId = source.sourceId,
        savedSelection = savedSelection,
        explicitPlayerName = preferredPlayerName,
        explicitQuality = preferredQuality,
        allowSavedSelection = allowSavedSelection,
    )
    val playerName = preferences.playerName
    val quality = preferences.quality
    val context = PlaybackContext(
        titleId = titleId,
        sourceId = source.sourceId,
        episodeId = episode.id,
        episodeNumber = episode.number,
        sourceTitle = source.title,
        animeTitle = animeTitle,
        episodes = episodes,
        selectedPlayerName = playerName,
        selectedQualityLabel = quality ?: source.qualityLabel,
        settingsOptions = settingsOptions,
    )
    return PreparedPlaybackRequest(
        source = source,
        playerName = playerName,
        quality = quality,
        episodes = episodes,
        context = context,
    )
}
