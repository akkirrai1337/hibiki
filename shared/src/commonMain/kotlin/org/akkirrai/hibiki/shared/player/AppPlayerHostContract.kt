package org.akkirrai.hibiki.shared.player

/** Platform-neutral state exposed to a player host. Media playback remains platform-owned. */
data class AppPlayerHostState(
    val sourceId: String,
    val episodeId: String,
    val episodeNumber: Double?,
    val animeTitle: String,
    val episodeIds: List<String>,
    val isLoading: Boolean,
    val errorMessage: String?,
    val hasPlayback: Boolean,
    val isSettingsLoading: Boolean,
    val selectedPlayerName: String?,
    val selectedQualityLabel: String?,
)

fun PlayerUiState.toAppPlayerHostState(): AppPlayerHostState = AppPlayerHostState(
    sourceId = currentSourceId,
    episodeId = currentEpisodeId,
    episodeNumber = currentEpisodeNumber,
    animeTitle = animeTitle,
    episodeIds = episodes.map { it.id },
    isLoading = isLoading,
    errorMessage = errorMessage,
    hasPlayback = playback != null,
    isSettingsLoading = isSettingsLoading,
    selectedPlayerName = selectedPlayerName,
    selectedQualityLabel = selectedQualityLabel,
)

sealed interface AppPlayerHostAction {
    data object Back : AppPlayerHostAction
    data object Retry : AppPlayerHostAction
    data class SelectEpisode(val episodeId: String) : AppPlayerHostAction
    data class SelectSource(val sourceId: String) : AppPlayerHostAction
    data class SelectQuality(val qualityLabel: String?) : AppPlayerHostAction
    data class SelectPlayer(val playerName: String?) : AppPlayerHostAction
}
