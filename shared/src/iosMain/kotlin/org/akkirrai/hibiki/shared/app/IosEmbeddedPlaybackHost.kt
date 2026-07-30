package org.akkirrai.hibiki.shared.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.design.component.AppBackButton
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.IosEmbeddedPlayerSurface
import org.akkirrai.hibiki.shared.player.AppPlayerFrame
import org.akkirrai.hibiki.shared.player.AppPlayerTopOverlay
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

@Composable
internal fun IosEmbeddedPlaybackHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    onBack: () -> Unit,
) {
    AppPlayerFrame {
        IosEmbeddedPlayerSurface(
            playback = playback,
            modifier = Modifier.fillMaxSize(),
        )
        AppPlayerTopOverlay(
            title = playback.animeTitle,
            subtitle = appText(AppTextKey.PlayerEpisodeNumber)
                .replace("%s", formatEpisodeNumber(context.episodeNumber)),
            playlistEnabled = false,
            backContent = {
                AppBackButton(
                    onClick = onBack,
                    contentDescription = appText(AppTextKey.Back),
                )
            },
            playlistContent = {},
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
