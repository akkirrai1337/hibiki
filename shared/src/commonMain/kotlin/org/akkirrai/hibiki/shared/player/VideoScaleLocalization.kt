package org.akkirrai.hibiki.shared.player

fun VideoScaleMode.localizationKey(): String = when (this) {
    VideoScaleMode.FIT -> "watch_player_video_scale_fit"
    VideoScaleMode.CROP -> "watch_player_video_scale_crop"
    VideoScaleMode.STRETCH -> "watch_player_video_scale_stretch"
}
