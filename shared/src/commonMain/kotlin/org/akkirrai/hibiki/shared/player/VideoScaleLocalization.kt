package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.text.AppTextKey

fun VideoScaleMode.localizationKey(): String = when (this) {
    VideoScaleMode.FIT -> "watch_player_video_scale_fit"
    VideoScaleMode.CROP -> "watch_player_video_scale_crop"
    VideoScaleMode.STRETCH -> "watch_player_video_scale_stretch"
}

fun VideoScaleMode.textKey(): AppTextKey = when (this) {
    VideoScaleMode.FIT -> AppTextKey.PlayerVideoScaleFit
    VideoScaleMode.CROP -> AppTextKey.PlayerVideoScaleCrop
    VideoScaleMode.STRETCH -> AppTextKey.PlayerVideoScaleStretch
}
