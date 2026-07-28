package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable

@Composable
fun resolvePlayerEpisodeSubtitle(
    state: PlayerUiState,
    episodeLabel: @Composable (String) -> String,
): String = resolveLocalizedEpisodeTitle(
    resolveCurrentEpisodeTitle(
        playbackTitle = state.playback?.episodeTitle,
        currentEpisodeId = state.currentEpisodeId,
        episodes = state.episodes,
    ),
    episodeLabel = episodeLabel,
)
