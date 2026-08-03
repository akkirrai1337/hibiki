package org.akkirrai.hibiki.feature.player

import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.player.VideoScaleMode
import org.akkirrai.hibiki.shared.player.resolveVideoScaleFactors

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

internal fun PlayerView.applyVideoScale(mode: VideoScaleMode, videoAspectRatio: Float) {
    val textureView = videoSurfaceView as? TextureView ?: return
    if (!textureView.isLaidOut || textureView.width == 0 || textureView.height == 0) {
        textureView.doOnLayout { applyVideoScale(mode, videoAspectRatio) }
        return
    }

    val containerAspectRatio = textureView.width.toFloat() / textureView.height
    val aspectRatioFactor = videoAspectRatio / containerAspectRatio
    val factors = resolveVideoScaleFactors(mode, aspectRatioFactor)
    val target = TextureVideoScale(mode, factors.scaleX, factors.scaleY)
    if (textureView.tag == target) return

    textureView.tag = target
    textureView.animate()
        .cancel()
    textureView.animate()
        .scaleX(factors.scaleX)
        .scaleY(factors.scaleY)
        .setDuration(PLAYER_VIDEO_SCALE_ANIMATION_DURATION_MS)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

private data class TextureVideoScale(
    val mode: VideoScaleMode,
    val scaleX: Float,
    val scaleY: Float,
)

private const val PLAYER_VIDEO_SCALE_ANIMATION_DURATION_MS = 220L
