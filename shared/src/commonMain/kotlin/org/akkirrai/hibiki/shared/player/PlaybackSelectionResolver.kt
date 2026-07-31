package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.PlaybackSelection

data class EffectivePlaybackPreferences(
    val playerName: String?,
    val quality: String?,
)

fun resolvePlaybackPreferences(
    sourceId: String,
    savedSelection: PlaybackSelection?,
    explicitPlayerName: String?,
    explicitQuality: String?,
    allowSavedSelection: Boolean,
): EffectivePlaybackPreferences {
    val saved = savedSelection?.takeIf { allowSavedSelection && it.sourceId == sourceId }
    return EffectivePlaybackPreferences(
        playerName = explicitPlayerName ?: saved?.playerName,
        quality = explicitQuality ?: saved?.quality,
    )
}
