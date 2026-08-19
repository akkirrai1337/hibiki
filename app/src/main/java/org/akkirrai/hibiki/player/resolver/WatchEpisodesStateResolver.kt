package org.akkirrai.hibiki.player

import org.akkirrai.hibiki.player.model.WatchEpisode

fun mergeWatchEpisodes(
    primary: List<WatchEpisode>,
    secondary: List<WatchEpisode>,
): List<WatchEpisode> = (primary + secondary)
    .associateBy(WatchEpisode::id)
    .values
    .sortedBy(WatchEpisode::number)

fun initialEpisodesState(offlineEpisodes: List<WatchEpisode>): EpisodesScreenState =
    EpisodesScreenState(
        result = if (offlineEpisodes.isEmpty()) {
            EpisodesUiState.Loading
        } else {
            EpisodesUiState.Content(offlineEpisodes)
        },
    )

fun loadedEpisodesState(
    episodes: List<WatchEpisode>,
    offlineEpisodes: List<WatchEpisode>,
): EpisodesScreenState {
    val merged = mergeWatchEpisodes(episodes, offlineEpisodes)
    return EpisodesScreenState(
        result = if (merged.isEmpty()) {
            EpisodesUiState.Empty
        } else {
            EpisodesUiState.Content(merged)
        },
    )
}

fun errorEpisodesState(
    message: String,
    offlineEpisodes: List<WatchEpisode>,
): EpisodesScreenState = EpisodesScreenState(
    result = if (offlineEpisodes.isNotEmpty()) {
        EpisodesUiState.Content(offlineEpisodes)
    } else {
        EpisodesUiState.Error(message)
    },
)
