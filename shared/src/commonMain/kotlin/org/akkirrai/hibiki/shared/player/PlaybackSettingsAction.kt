package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchSource

/** Actions emitted by the common player settings content to the host orchestration layer. */
sealed interface PlaybackSettingsAction {
    data class SelectVoiceover(val source: WatchSource) : PlaybackSettingsAction
    data class SelectPlayer(val playerName: String?) : PlaybackSettingsAction
    data class SelectQuality(val qualityLabel: String?) : PlaybackSettingsAction
    data class SetAutoSkipSegments(val enabled: Boolean) : PlaybackSettingsAction
    data class SetAutoPlayNextEpisode(val enabled: Boolean) : PlaybackSettingsAction
}
