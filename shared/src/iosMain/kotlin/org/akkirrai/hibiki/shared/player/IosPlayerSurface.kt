package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import org.akkirrai.hibiki.shared.model.PlaybackStream
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun IosPlayerSurface(
    playback: PlaybackStream,
    modifier: Modifier = Modifier,
) {
    UIKitView(
        factory = { IosVideoView() },
        update = { view -> view.setPlayback(playback) },
        onRelease = { view -> view.releasePlayback() },
        modifier = modifier,
    )
}

@OptIn(ExperimentalForeignApi::class)
private class IosVideoView : UIView(frame = CGRectZero.readValue()) {
    private var player: AVPlayer? = null
    private var playerLayer: AVPlayerLayer? = null
    private var currentUrl: String? = null

    fun setPlayback(playback: PlaybackStream) {
        if (currentUrl == playback.streamUrl && player != null) return
        releasePlayback()

        val headers = buildMap {
            put("User-Agent", "Hibiki/0.1 iOS")
            putAll(playback.headers)
        }
        val asset = AVURLAsset(
            uRL = NSURL(string = playback.streamUrl),
            options = mapOf<Any?, Any?>(
                "AVURLAssetHTTPHeaderFieldsKey" to headers,
            ),
        )
        val nextPlayer = AVPlayer(playerItem = AVPlayerItem(asset = asset))
        val nextLayer = AVPlayerLayer.playerLayerWithPlayer(nextPlayer)
        nextLayer.videoGravity = AVLayerVideoGravityResizeAspect
        layer.addSublayer(nextLayer)
        player = nextPlayer
        playerLayer = nextLayer
        currentUrl = playback.streamUrl
    }

    fun releasePlayback() {
        playerLayer?.removeFromSuperlayer()
        playerLayer = null
        player = null
        currentUrl = null
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer?.frame = bounds
    }
}
