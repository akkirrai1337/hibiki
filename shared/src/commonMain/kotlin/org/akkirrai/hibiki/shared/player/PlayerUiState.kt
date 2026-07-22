package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.WatchEpisode

data class PlayerUiState(
    val isLoading: Boolean = true,
    val playback: PlaybackStream? = null,
    val animeTitle: String = "",
    val episodes: List<WatchEpisode> = emptyList(),
    val currentSourceId: String = "",
    val currentEpisodeId: String = "",
    val currentEpisodeNumber: Double? = null,
    val pendingSeekMs: Long = 0L,
    val errorMessage: String? = null,
    val failedStreamUrls: Set<String> = emptySet(),
    val recoveryAttempted: Boolean = false,
    val isSettingsLoading: Boolean = false,
    val settingsOptions: PlaybackSettingsOptions = PlaybackSettingsOptions(),
    val settingsOptionsKey: String? = null,
    val selectedPlayerName: String? = null,
    val selectedQualityLabel: String? = null,
)
