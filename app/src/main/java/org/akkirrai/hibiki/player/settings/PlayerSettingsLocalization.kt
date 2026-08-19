package org.akkirrai.hibiki.player

import org.akkirrai.hibiki.app.navigation.AppPlayerSettingsDestination as PlayerSettingsDestination

fun PlayerSettingsDestination.localizationKey(): String = when (this) {
    PlayerSettingsDestination.Root -> "watch_player_settings_root"
    PlayerSettingsDestination.Speed -> "watch_player_settings_speed"
    PlayerSettingsDestination.Voiceover -> "watch_player_settings_voiceover"
    PlayerSettingsDestination.Player -> "watch_player_settings_player"
    PlayerSettingsDestination.Quality -> "watch_player_settings_quality"
}
