package org.akkirrai.hibiki.shared.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.AppPlayerFrame
import org.akkirrai.hibiki.shared.player.AppPlayerPlaylistLayer
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsContent
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsLayer
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.PlayerSettingsDestination
import org.akkirrai.hibiki.shared.player.resolveAdjacentEpisode
import org.akkirrai.hibiki.shared.player.resolveEpisodeNavigationAvailability
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
import org.akkirrai.hibiki.shared.player.IosComposePlayerControls
import org.akkirrai.hibiki.shared.player.IosPlayerSession
import org.akkirrai.hibiki.shared.player.IosPlayerSurface
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import platform.Foundation.NSDate

@Composable
internal fun IosEmbeddedPlaybackHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    onBack: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    settingsStore: AppSettingsStore,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
) {
    val session = remember(playback.streamUrl, playback.headers) { IosPlayerSession(playback) }
    var playlistVisible by remember(session) { mutableStateOf(false) }
    var settingsVisible by remember(session) { mutableStateOf(false) }
    var settingsDestination by remember(session) { mutableStateOf(PlayerSettingsDestination.Root) }
    var selectedSpeed by remember(session) { mutableFloatStateOf(settingsStore.load().playbackSpeed) }
    var autoSkipSegments by remember(session) { mutableStateOf(settingsStore.load().autoSkipSegments) }
    var autoPlayNextEpisode by remember(session) { mutableStateOf(settingsStore.load().autoPlayNextEpisode) }
    val episodeNavigation = resolveEpisodeNavigationAvailability(context.episodes, context.episodeId)
    AppPlayerFrame {
        IosPlayerSurface(session, session.scaleMode, Modifier.fillMaxSize())
        IosComposePlayerControls(
            session = session,
            playback = playback,
            context = context,
            onBack = onBack,
            playlistEnabled = context.episodes.isNotEmpty(),
            onPlaylistClick = { playlistVisible = true },
            hasPreviousEpisode = episodeNavigation.hasPrevious,
            hasNextEpisode = episodeNavigation.hasNext,
            onPreviousEpisode = {
                resolveAdjacentEpisode(context.episodes, context.episodeId, context.episodeNumber, -1)
                    ?.let(onEpisodeSelected)
            },
            onNextEpisode = {
                resolveAdjacentEpisode(context.episodes, context.episodeId, context.episodeNumber, 1)
                    ?.let(onEpisodeSelected)
            },
            onSettingsClick = {
                settingsDestination = PlayerSettingsDestination.Root
                settingsVisible = true
            },
            settingsContentDescription = appText(AppTextKey.Settings),
        )
        AppPlayerPlaylistLayer(
            visible = playlistVisible,
            currentEpisodeId = context.episodeId,
            episodes = context.episodes,
            headline = { episode ->
                appText(AppTextKey.PlayerEpisodeNumber).replace("%s", formatEpisodeNumber(episode.number))
            },
            onDismissRequest = { playlistVisible = false },
            onEpisodeClick = { episodeId ->
                context.episodes.firstOrNull { it.id == episodeId }?.let(onEpisodeSelected)
            },
            nowMs = { (NSDate().timeIntervalSince1970 * 1_000.0).toLong() },
            backHandler = { _, _ -> },
        )
        if (settingsVisible) {
            AppPlayerSettingsLayer(
                onDismissRequest = {
                    settingsVisible = false
                    settingsDestination = PlayerSettingsDestination.Root
                },
                nowMs = { (NSDate().timeIntervalSince1970 * 1_000.0).toLong() },
                backHandler = { _, _ -> },
            ) { dismissPanel ->
                AppPlayerSettingsContent(
                    destination = settingsDestination,
                    selectedSpeed = selectedSpeed,
                    selectedSourceId = context.sourceId,
                    selectedPlayerName = context.selectedPlayerName,
                    selectedQualityLabel = context.selectedQualityLabel ?: playback.qualityLabel,
                    availableQualityLabels = playback.availableQualityLabels,
                    autoSkipSegments = autoSkipSegments,
                    autoPlayNextEpisode = autoPlayNextEpisode,
                    options = context.settingsOptions,
                    onNavigate = { settingsDestination = it },
                    onBack = { settingsDestination = PlayerSettingsDestination.Root },
                    backHandler = { _, _ -> },
                    onSelectSpeed = { speed ->
                        selectedSpeed = speed
                        session.transport.setRate(speed)
                        settingsStore.save(settingsStore.load().copy(playbackSpeed = speed))
                    },
                    onSelectVoiceover = { source ->
                        dismissPanel()
                        settingsVisible = false
                        onSettingsAction(PlaybackSettingsAction.SelectVoiceover(source))
                    },
                    onSelectPlayer = { playerName ->
                        dismissPanel()
                        settingsVisible = false
                        onSettingsAction(PlaybackSettingsAction.SelectPlayer(playerName))
                    },
                    onSelectQuality = { qualityLabel ->
                        dismissPanel()
                        settingsVisible = false
                        onSettingsAction(PlaybackSettingsAction.SelectQuality(qualityLabel))
                    },
                    onAutoSkipSegmentsChange = { enabled ->
                        autoSkipSegments = enabled
                        onSettingsAction(PlaybackSettingsAction.SetAutoSkipSegments(enabled))
                    },
                    onAutoPlayNextEpisodeChange = { enabled ->
                        autoPlayNextEpisode = enabled
                        onSettingsAction(PlaybackSettingsAction.SetAutoPlayNextEpisode(enabled))
                    },
                )
            }
        }
    }
}
