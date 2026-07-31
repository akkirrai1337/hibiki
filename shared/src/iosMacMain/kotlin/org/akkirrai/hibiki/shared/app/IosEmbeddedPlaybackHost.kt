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
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppOverlay
import org.akkirrai.hibiki.shared.player.AppPlayerFrame
import org.akkirrai.hibiki.shared.player.AppPlayerPlaylistLayer
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsContent
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsLayer
import org.akkirrai.hibiki.shared.player.AppPlayerSkipSegmentLayer
import org.akkirrai.hibiki.shared.player.AppPlayerUnlockOverlay
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.PlayerSettingsDestination
import org.akkirrai.hibiki.shared.player.resolveAdjacentEpisode
import org.akkirrai.hibiki.shared.player.resolveEpisodeNavigationAvailability
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
import org.akkirrai.hibiki.shared.player.resolveActivePlaybackSegment
import org.akkirrai.hibiki.shared.player.buildSkipSegmentKey
import org.akkirrai.hibiki.shared.player.resolveAutoPlayNextEpisode
import org.akkirrai.hibiki.shared.player.resolvePersistablePlaybackProgress
import org.akkirrai.hibiki.shared.player.PlaybackProgressCoordinator
import org.akkirrai.hibiki.shared.player.PlayerUnlockBottomPadding
import org.akkirrai.hibiki.shared.player.sessionKey
import org.akkirrai.hibiki.shared.platform.AppSystemBackHandler
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository
import org.akkirrai.hibiki.shared.player.IosComposePlayerControls
import org.akkirrai.hibiki.shared.player.IosPlayerSession
import org.akkirrai.hibiki.shared.player.IosPlayerSurface
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationWillResignActiveNotification
import kotlinx.coroutines.delay

@Composable
internal fun IosEmbeddedPlaybackHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    navigationState: AppNavigationState,
    onBack: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    settingsStore: AppSettingsStore,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
    progressRepository: PlaybackProgressRepository,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    val session = remember(playback.sessionKey()) {
        IosPlayerSession(playback).also {
            it.scaleMode = settingsStore.load().videoScaleMode
            it.transport.setRate(settingsStore.load().playbackSpeed)
        }
    }
    val progressCoordinator = remember(session) {
        PlaybackProgressCoordinator { progress ->
            progressRepository.saveEpisodeProgress(
                context = context,
                playback = playback,
                positionMs = progress.positionMs,
                durationMs = progress.durationMs,
            )
        }
    }
    val playlistVisible = navigationState.overlays.lastOrNull() == AppOverlay.Playlist
    val settingsVisible = navigationState.overlays.lastOrNull() == AppOverlay.PlayerSettings
    var selectedSpeed by remember(session) { mutableFloatStateOf(settingsStore.load().playbackSpeed) }
    var autoSkipSegments by remember(session) { mutableStateOf(settingsStore.load().autoSkipSegments) }
    var autoPlayNextEpisode by remember(session) { mutableStateOf(settingsStore.load().autoPlayNextEpisode) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var unlockButtonVisible by remember { mutableStateOf(false) }
    var positionMs by remember(session) { mutableLongStateOf(0L) }
    var completionHandled by remember(session) { mutableStateOf(false) }
    var hiddenSkipSegmentKey by remember(context.episodeId) { mutableStateOf<String?>(null) }
    var skipCountdownSeconds by remember { mutableIntStateOf(SKIP_SEGMENT_COUNTDOWN_SECONDS) }
    val episodeNavigation = resolveEpisodeNavigationAvailability(context.episodes, context.episodeId)

    fun savePlaybackProgress() {
        val position = session.transport.positionMs()
        resolvePersistablePlaybackProgress(position, session.transport.durationMs())?.let { progress ->
            progressCoordinator.persistIfChanged(progress)
        }
    }

    fun closePlayback() {
        savePlaybackProgress()
        onBack()
    }

    fun selectAdjacentEpisode(offset: Int) {
        controlsVisible = true
        savePlaybackProgress()
        resolveAdjacentEpisode(
            context.episodes,
            context.episodeId,
            context.episodeNumber,
            offset,
        )?.let(onEpisodeSelected)
    }

    fun dispatchSettingsAction(action: PlaybackSettingsAction) {
        controlsVisible = true
        savePlaybackProgress()
        onSettingsAction(action)
    }

    DisposableEffect(session) {
        val backgroundObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            obj = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { savePlaybackProgress() },
        )
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(backgroundObserver)
            savePlaybackProgress()
            session.release()
        }
    }
    LaunchedEffect(session, context.episodeId) {
        while (true) {
            positionMs = session.transport.positionMs()
            resolveAutoPlayNextEpisode(
                episodes = context.episodes,
                currentEpisodeId = context.episodeId,
                currentEpisodeNumber = context.episodeNumber,
                positionMs = positionMs,
                durationMs = session.transport.durationMs(),
                autoPlayEnabled = settingsStore.load().autoPlayNextEpisode,
                completionHandled = completionHandled,
            )?.let {
                completionHandled = true
                controlsVisible = true
                savePlaybackProgress()
                onEpisodeSelected(it)
            }
            delay(500L)
        }
    }
    val rawActiveSkipSegment = resolveActivePlaybackSegment(playback.segments, positionMs)
        ?.takeIf { controlsVisible && !controlsLocked && !playlistVisible && !settingsVisible }
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
        if (!controlsLocked) {
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
                onPlaylistClick = {
                    onOverlayEvent(AppNavigationEvent.PresentOverlay(AppOverlay.Playlist))
                },
                hasPreviousEpisode = episodeNavigation.hasPrevious,
                hasNextEpisode = episodeNavigation.hasNext,
                onPreviousEpisode = {
                    selectAdjacentEpisode(-1)
                },
                onNextEpisode = {
                    selectAdjacentEpisode(1)
                },
                onSettingsClick = {
                    onOverlayEvent(AppNavigationEvent.OpenPlayerSettings)
                },
                settingsContentDescription = appText(AppTextKey.Settings),
                onLockClick = {
                    controlsLocked = true
                    controlsVisible = false
                    unlockButtonVisible = true
                    if (playlistVisible) {
                        onOverlayEvent(AppNavigationEvent.DismissOverlay)
                    }
                    if (settingsVisible) {
                        onOverlayEvent(AppNavigationEvent.ClosePlayerSettings)
                    }
                },
                lockContentDescription = appText(AppTextKey.PlayerLock),
                onControlsVisibilityChanged = { controlsVisible = it },
                pictureInPictureEnabled = session.pictureInPictureController != null,
                onPictureInPictureClick = {
                    session.pictureInPictureController?.startPictureInPicture()
                },
                pictureInPictureContentDescription = appText(AppTextKey.PlayerPictureInPicture),
            )
        }
        AppPlayerUnlockOverlay(
            visible = controlsLocked && unlockButtonVisible,
            label = appText(AppTextKey.PlayerUnlock),
            onClick = {
                controlsLocked = false
                unlockButtonVisible = false
                controlsVisible = true
            },
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PlayerUnlockBottomPadding),
            includeSystemBottomInset = true,
        )
        AppPlayerPlaylistLayer(
            visible = playlistVisible,
            currentEpisodeId = context.episodeId,
            episodes = context.episodes,
            headline = { episode ->
                appText(AppTextKey.PlayerEpisodeNumber).replace("%s", formatEpisodeNumber(episode.number))
            },
            onDismissRequest = {
                onOverlayEvent(AppNavigationEvent.DismissOverlay)
                controlsVisible = true
            },
            onEpisodeClick = { episodeId ->
                context.episodes.firstOrNull { it.id == episodeId }?.let {
                    controlsVisible = true
                    savePlaybackProgress()
                    onEpisodeSelected(it)
                }
            },
            nowMs = { (NSDate().timeIntervalSince1970 * 1_000.0).toLong() },
            backHandler = { enabled, callback ->
                AppSystemBackHandler(enabled = enabled, onBack = callback) {}
            },
        )
        if (settingsVisible) {
            AppPlayerSettingsLayer(
                onDismissRequest = {
                onOverlayEvent(AppNavigationEvent.ClosePlayerSettings)
                controlsVisible = true
                },
                nowMs = { (NSDate().timeIntervalSince1970 * 1_000.0).toLong() },
                backHandler = { enabled, callback ->
                    AppSystemBackHandler(enabled = enabled, onBack = callback) {}
                },
            ) { dismissPanel ->
                AppPlayerSettingsContent(
                    destination = navigationState.playerSettingsDestination,
                    selectedSpeed = selectedSpeed,
                    selectedSourceId = context.sourceId,
                    selectedPlayerName = context.selectedPlayerName,
                    selectedQualityLabel = context.selectedQualityLabel ?: playback.qualityLabel,
                    availableQualityLabels = playback.availableQualityLabels,
                    autoSkipSegments = autoSkipSegments,
                    autoPlayNextEpisode = autoPlayNextEpisode,
                    options = context.settingsOptions,
                    onNavigate = {
                        controlsVisible = true
                        onOverlayEvent(AppNavigationEvent.SetPlayerSettingsDestination(it))
                    },
                    onBack = {
                        controlsVisible = true
                        onOverlayEvent(AppNavigationEvent.SetPlayerSettingsDestination(PlayerSettingsDestination.Root))
                    },
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
                        dispatchSettingsAction(PlaybackSettingsAction.SelectVoiceover(source))
                    },
                    onSelectPlayer = { playerName ->
                        dismissPanel()
                        dispatchSettingsAction(PlaybackSettingsAction.SelectPlayer(playerName))
                    },
                    onSelectQuality = { qualityLabel ->
                        dismissPanel()
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
