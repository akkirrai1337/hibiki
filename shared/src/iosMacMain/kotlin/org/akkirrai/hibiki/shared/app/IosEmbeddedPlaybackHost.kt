package org.akkirrai.hibiki.shared.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.AppPlayerFrame
import org.akkirrai.hibiki.shared.player.AppPlayerPlaylistLayer
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsContent
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsLayer
import org.akkirrai.hibiki.shared.player.AppPlayerSkipSegmentLayer
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.PlayerSettingsDestination
import org.akkirrai.hibiki.shared.player.resolveAdjacentEpisode
import org.akkirrai.hibiki.shared.player.resolveEpisodeNavigationAvailability
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
import org.akkirrai.hibiki.shared.player.resolveActivePlaybackSegment
import org.akkirrai.hibiki.shared.player.buildSkipSegmentKey
import org.akkirrai.hibiki.shared.player.isPlaybackComplete
import org.akkirrai.hibiki.shared.platform.AppSystemBackHandler
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository
import org.akkirrai.hibiki.shared.player.IosComposePlayerControls
import org.akkirrai.hibiki.shared.player.IosPlayerSession
import org.akkirrai.hibiki.shared.player.IosPlayerSurface
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import platform.Foundation.NSDate
import kotlinx.coroutines.delay

@Composable
internal fun IosEmbeddedPlaybackHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    onBack: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    settingsStore: AppSettingsStore,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
    progressRepository: PlaybackProgressRepository,
) {
    val session = remember(playback.streamUrl, playback.headers) {
        IosPlayerSession(playback).also {
            it.scaleMode = settingsStore.load().videoScaleMode
            it.transport.setRate(settingsStore.load().playbackSpeed)
        }
    }
    var playlistVisible by remember(session) { mutableStateOf(false) }
    var settingsVisible by remember(session) { mutableStateOf(false) }
    var settingsDestination by remember(session) { mutableStateOf(PlayerSettingsDestination.Root) }
    var selectedSpeed by remember(session) { mutableFloatStateOf(settingsStore.load().playbackSpeed) }
    var autoSkipSegments by remember(session) { mutableStateOf(settingsStore.load().autoSkipSegments) }
    var autoPlayNextEpisode by remember(session) { mutableStateOf(settingsStore.load().autoPlayNextEpisode) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember(session) { mutableLongStateOf(0L) }
    var completionHandled by remember(session) { mutableStateOf(false) }
    var hiddenSkipSegmentKey by remember(context.episodeId) { mutableStateOf<String?>(null) }
    var skipCountdownSeconds by remember { mutableIntStateOf(SKIP_SEGMENT_COUNTDOWN_SECONDS) }
    val episodeNavigation = resolveEpisodeNavigationAvailability(context.episodes, context.episodeId)

    fun savePlaybackProgress() {
        val position = session.transport.positionMs()
        if (position <= 0L) return
        progressRepository.saveEpisodeProgress(
            context = context,
            playback = playback,
            positionMs = position,
            durationMs = session.transport.durationMs(),
        )
    }

    fun closePlayback() {
        savePlaybackProgress()
        onBack()
    }

    fun dispatchSettingsAction(action: PlaybackSettingsAction) {
        savePlaybackProgress()
        onSettingsAction(action)
    }

    DisposableEffect(session) {
        onDispose {
            savePlaybackProgress()
            session.release()
        }
    }
    LaunchedEffect(session, context.episodeId) {
        while (true) {
            positionMs = session.transport.positionMs()
            if (!completionHandled &&
                settingsStore.load().autoPlayNextEpisode &&
                isPlaybackComplete(positionMs, session.transport.durationMs())
            ) {
                completionHandled = true
                resolveAdjacentEpisode(context.episodes, context.episodeId, context.episodeNumber, 1)
                    ?.let(onEpisodeSelected)
            }
            delay(500L)
        }
    }
    val rawActiveSkipSegment = resolveActivePlaybackSegment(playback.segments, positionMs)
        ?.takeIf { controlsVisible && !playlistVisible && !settingsVisible }
    val activeSkipSegmentKey = rawActiveSkipSegment?.let { buildSkipSegmentKey(context.episodeId, it) }
    val activeSkipSegment = rawActiveSkipSegment?.takeIf { hiddenSkipSegmentKey != activeSkipSegmentKey }
    LaunchedEffect(activeSkipSegmentKey, settingsStore.load().autoSkipSegments) {
        val key = activeSkipSegmentKey ?: run {
            skipCountdownSeconds = SKIP_SEGMENT_COUNTDOWN_SECONDS
            return@LaunchedEffect
        }
        val segment = rawActiveSkipSegment
        skipCountdownSeconds = SKIP_SEGMENT_COUNTDOWN_SECONDS
        repeat(SKIP_SEGMENT_COUNTDOWN_SECONDS) {
            delay(1_000L)
            if (hiddenSkipSegmentKey == key) return@LaunchedEffect
            skipCountdownSeconds = (skipCountdownSeconds - 1).coerceAtLeast(0)
        }
        if (settingsStore.load().autoSkipSegments && hiddenSkipSegmentKey != key) {
            session.transport.seekToMs(segment.endMs)
        } else if (hiddenSkipSegmentKey != key) {
            hiddenSkipSegmentKey = key
        }
    }
    AppPlayerFrame {
        IosPlayerSurface(session, session.scaleMode, Modifier.fillMaxSize())
        IosComposePlayerControls(
            session = session,
            playback = playback,
            context = context,
            onBack = ::closePlayback,
            onScaleClick = {
                session.scaleMode = session.scaleMode.next()
                settingsStore.save(settingsStore.load().copy(videoScaleMode = session.scaleMode))
            },
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
            onControlsVisibilityChanged = { controlsVisible = it },
            pictureInPictureEnabled = session.pictureInPictureController != null,
            onPictureInPictureClick = {
                session.pictureInPictureController?.startPictureInPicture()
            },
            pictureInPictureContentDescription = appText(AppTextKey.PlayerPictureInPicture),
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
            backHandler = { enabled, callback ->
                AppSystemBackHandler(enabled = enabled, onBack = callback) {}
            },
        )
        if (settingsVisible) {
            AppPlayerSettingsLayer(
                onDismissRequest = {
                    settingsVisible = false
                    settingsDestination = PlayerSettingsDestination.Root
                },
                nowMs = { (NSDate().timeIntervalSince1970 * 1_000.0).toLong() },
                backHandler = { enabled, callback ->
                    AppSystemBackHandler(enabled = enabled, onBack = callback) {}
                },
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
                    backHandler = { enabled, callback ->
                        AppSystemBackHandler(enabled = enabled, onBack = callback) {}
                    },
                    onSelectSpeed = { speed ->
                        selectedSpeed = speed
                        session.transport.setRate(speed)
                        settingsStore.save(settingsStore.load().copy(playbackSpeed = speed))
                    },
                    onSelectVoiceover = { source ->
                        dismissPanel()
                        settingsVisible = false
                        dispatchSettingsAction(PlaybackSettingsAction.SelectVoiceover(source))
                    },
                    onSelectPlayer = { playerName ->
                        dismissPanel()
                        settingsVisible = false
                        dispatchSettingsAction(PlaybackSettingsAction.SelectPlayer(playerName))
                    },
                    onSelectQuality = { qualityLabel ->
                        dismissPanel()
                        settingsVisible = false
                        dispatchSettingsAction(PlaybackSettingsAction.SelectQuality(qualityLabel))
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
        activeSkipSegment?.let { segment ->
            AppPlayerSkipSegmentLayer(
                visible = true,
                controlsVisible = controlsVisible,
                countdownSeconds = skipCountdownSeconds,
                maxCountdownSeconds = SKIP_SEGMENT_COUNTDOWN_SECONDS,
                autoSkipEnabled = settingsStore.load().autoSkipSegments,
                skipLabel = appText(AppTextKey.PlayerSkip),
                watchLabel = appText(AppTextKey.PlayerWatch),
                onSkipClick = { session.transport.seekToMs(segment.endMs) },
                onWatchClick = { activeSkipSegmentKey?.let { hiddenSkipSegmentKey = it } },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

private const val SKIP_SEGMENT_COUNTDOWN_SECONDS = 10
