package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable

@Composable
fun resolvePlayerEpisodeSubtitle(
    state: PlayerUiState,
    episodeLabel: @Composable (String) -> String,
): String = resolveLocalizedEpisodeTitle(
    title = resolveCurrentEpisodeTitle(
        playbackTitle = state.playback?.episodeTitle,
        currentEpisodeId = state.currentEpisodeId,
        episodes = state.episodes,
    ).let { fallbackTitle ->
        if (state.playback?.episodeTitle.isNullOrBlank()) {
            resolveCurrentEpisodeNumber(state.currentEpisodeId, state.episodes)
                ?.let { number -> episodeLabel(number) }
                ?: fallbackTitle
        } else {
            fallbackTitle
        }
    },
    episodeLabel = episodeLabel,
)
