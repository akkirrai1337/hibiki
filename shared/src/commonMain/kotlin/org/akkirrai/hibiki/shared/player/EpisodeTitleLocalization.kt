package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable

@Composable
fun resolveLocalizedEpisodeTitle(
    title: String,
    episodeLabel: @Composable (number: String) -> String,
): String {
    val fallbackEpisodeNumber = fallbackEpisodeNumberFromTitle(title)
    return if (fallbackEpisodeNumber != null) episodeLabel(fallbackEpisodeNumber) else title
}

@Composable
fun resolveEpisodeNumberTitle(
    episodeNumber: Double,
    episodeLabel: @Composable (number: String) -> String,
): String = episodeLabel(formatEpisodeNumber(episodeNumber))
