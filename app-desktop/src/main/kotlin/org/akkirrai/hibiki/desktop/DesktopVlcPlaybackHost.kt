package org.akkirrai.hibiki.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream

/** Embedded Desktop video surface; shared controls are layered in a later step. */
@Composable
internal fun DesktopVlcPlaybackHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    onBack: () -> Unit,
) {
    val session = remember(playback.streamUrl, playback.headers) {
        DesktopVlcPlaybackSession(playback)
    }
    DisposableEffect(session) {
        onDispose { session.release() }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        SwingPanel(
            factory = { session.component },
            modifier = Modifier.fillMaxSize(),
            update = {},
        )
    }
}
