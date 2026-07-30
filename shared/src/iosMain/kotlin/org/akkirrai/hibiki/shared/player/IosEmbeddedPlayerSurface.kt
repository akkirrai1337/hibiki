package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import org.akkirrai.hibiki.shared.model.PlaybackStream
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

@Composable
internal fun IosEmbeddedPlayerSurface(
    playback: PlaybackStream,
    modifier: Modifier = Modifier,
) {
    val controller = remember(playback.streamUrl, playback.headers) {
        AVPlayerViewController().apply {
            player = playback.toIosPlayer()
        }
    }
    UIKitViewController(
        factory = { controller },
        onRelease = { releasedController -> releasedController.player = null },
        modifier = modifier,
    )
}

private fun PlaybackStream.toIosPlayer(): AVPlayer {
    val headers = buildMap {
        put("User-Agent", "Hibiki/0.1 iOS")
        putAll(this@toIosPlayer.headers)
    }
    val asset = AVURLAsset(
        uRL = NSURL(string = streamUrl),
        options = mapOf<Any?, Any?>(
            "AVURLAssetHTTPHeaderFieldsKey" to headers,
        ),
    )
    return AVPlayer(playerItem = AVPlayerItem(asset = asset))
}
