package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream

@Composable
internal fun IosComposePlayerControls(
    session: IosPlayerSession,
    playback: PlaybackStream,
    context: PlaybackContext,
    onBack: () -> Unit,
) {
    AppPlaybackControls(
        transport = session.transport,
        playback = playback,
        context = context,
        scaleMode = session.scaleMode,
        onScaleClick = { session.scaleMode = session.scaleMode.next() },
        onBack = onBack,
    )
}
