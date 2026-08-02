package org.akkirrai.hibiki.feature.player

import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.player.VideoScaleMode

@Composable
internal fun AndroidPlayerSurface(
    exoPlayer: ExoPlayer,
    isAudioOnly: Boolean,
    videoScaleMode: VideoScaleMode,
    videoAspectRatio: Float,
    isClosing: Boolean,
    onAttached: (PlayerView) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isClosing) AndroidView(
        factory = { viewContext ->
            (LayoutInflater.from(viewContext)
                .inflate(R.layout.view_media3_player, null, false) as PlayerView)
                .apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    player = exoPlayer
                    applyVideoScale(videoScaleMode, videoAspectRatio)
                    onAttached(this)
                }
        },
        update = { playerView ->
            onAttached(playerView)
            playerView.player = if (isAudioOnly) null else exoPlayer
            playerView.applyVideoScale(videoScaleMode, videoAspectRatio)
        },
        modifier = modifier.fillMaxSize(),
    )
}
