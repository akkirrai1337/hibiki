package org.akkirrai.hibiki.shared.player

fun pictureInPictureAudioModeLocalizationKey(isAudioOnly: Boolean): String =
    if (isAudioOnly) "watch_player_show_video" else "watch_player_audio_only"

fun pictureInPicturePlaybackLocalizationKey(isPlaying: Boolean): String =
    if (isPlaying) "watch_player_pause" else "watch_player_play"
