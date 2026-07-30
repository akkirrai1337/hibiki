package org.akkirrai.hibiki.shared.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.AppPlayerFrame
import org.akkirrai.hibiki.shared.player.IosComposePlayerControls
import org.akkirrai.hibiki.shared.player.IosPlayerSession
import org.akkirrai.hibiki.shared.player.IosPlayerSurface

@Composable
internal fun IosEmbeddedPlaybackHost(playback: PlaybackStream, context: PlaybackContext, onBack: () -> Unit) {
    val session = remember(playback.streamUrl, playback.headers) { IosPlayerSession(playback) }
    AppPlayerFrame {
        IosPlayerSurface(session, session.scaleMode, Modifier.fillMaxSize())
        IosComposePlayerControls(session, playback, context, onBack)
    }
}
