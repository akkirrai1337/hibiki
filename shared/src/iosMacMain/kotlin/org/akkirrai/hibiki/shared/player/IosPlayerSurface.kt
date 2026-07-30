package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import org.akkirrai.hibiki.shared.model.PlaybackStream
import platform.AVFoundation.AVLayerVideoGravityResize
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVURLAsset
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
internal class IosPlayerSession(playback: PlaybackStream) {
    val player = playback.toIosPlayer()
    val transport = IosAvPlayerTransport(player)
    var scaleMode by mutableStateOf(VideoScaleMode.FIT)

    fun release() = transport.pause()
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun IosPlayerSurface(
    session: IosPlayerSession,
    scaleMode: VideoScaleMode,
    modifier: Modifier = Modifier,
) {
    UIKitView(
        factory = { IosVideoView() },
        update = { view -> view.bind(session.player, scaleMode) },
        onRelease = { view -> view.releasePlayerLayer() },
        modifier = modifier,
    )
}

@OptIn(ExperimentalForeignApi::class)
private class IosVideoView : UIView(frame = CGRectZero.readValue()) {
    private var playerLayer: AVPlayerLayer? = null

    fun bind(player: AVPlayer, scaleMode: VideoScaleMode) {
        val layer = playerLayer
        if (layer?.player != player) {
            layer?.removeFromSuperlayer()
            playerLayer = AVPlayerLayer.playerLayerWithPlayer(player).also { nextLayer ->
                this.layer.addSublayer(nextLayer)
            }
        }
        playerLayer?.videoGravity = scaleMode.toIosVideoGravity()
        playerLayer?.frame = bounds
        userInteractionEnabled = false
    }

    fun releasePlayerLayer() {
        playerLayer?.removeFromSuperlayer()
        playerLayer = null
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer?.frame = bounds
    }
}

private fun PlaybackStream.toIosPlayer(): AVPlayer {
    val headers = buildMap {
        put("User-Agent", "Hibiki/0.1 iOS")
        putAll(this@toIosPlayer.headers)
    }
    val asset = AVURLAsset(
        uRL = NSURL(string = streamUrl),
        options = mapOf<Any?, Any?>("AVURLAssetHTTPHeaderFieldsKey" to headers),
    )
    return AVPlayer(playerItem = AVPlayerItem(asset = asset))
}

private fun VideoScaleMode.toIosVideoGravity(): String = when (this) {
    VideoScaleMode.FIT -> AVLayerVideoGravityResizeAspect
    VideoScaleMode.CROP -> AVLayerVideoGravityResizeAspectFill
    VideoScaleMode.STRETCH -> AVLayerVideoGravityResize
}.orEmpty()
