package org.akkirrai.hibiki.shared.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppOverlay
import org.akkirrai.hibiki.shared.navigation.isPlayerSettingsOverlayActive
import org.akkirrai.hibiki.shared.navigation.isPlaylistOverlayActive
import org.akkirrai.hibiki.shared.player.AppPlayerFrame
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsContent
import org.akkirrai.hibiki.shared.player.AppPlayerPanelOverlays
import org.akkirrai.hibiki.shared.player.AppPlayerOverlayStack
import org.akkirrai.hibiki.shared.player.AppPlayerChrome
import org.akkirrai.hibiki.shared.player.AppPlaybackControls
import org.akkirrai.hibiki.shared.player.dispatchAdjacentPlayerEpisodeSelection
import org.akkirrai.hibiki.shared.player.dispatchPlayerEpisodeSelection
import org.akkirrai.hibiki.shared.player.dispatchPlayerClose
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsSelection
import org.akkirrai.hibiki.shared.player.dispatchPlayerPlaylistOpen
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsOpen
import org.akkirrai.hibiki.shared.player.dispatchPlayerPlaylistDismiss
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsDismiss
import org.akkirrai.hibiki.shared.player.dispatchPlayerLock
import org.akkirrai.hibiki.shared.player.dispatchPlayerUnlock
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsDestination
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsRoot
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.DefaultSkipSegmentCountdownSeconds
import org.akkirrai.hibiki.shared.player.resolveAdjacentEpisode
import org.akkirrai.hibiki.shared.player.resolveEpisodeNavigationAvailability
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
import org.akkirrai.hibiki.shared.player.resolveActivePlaybackSegment
import org.akkirrai.hibiki.shared.player.buildSkipSegmentKey
import org.akkirrai.hibiki.shared.player.shouldShowSkipSegmentPrompt
import org.akkirrai.hibiki.shared.player.textKey
import org.akkirrai.hibiki.shared.player.resolveAutoPlayNextEpisode
import org.akkirrai.hibiki.shared.player.PlaybackProgressCoordinator
import org.akkirrai.hibiki.shared.player.PlayerUnlockBottomPadding
import org.akkirrai.hibiki.shared.player.sessionKey
import org.akkirrai.hibiki.shared.platform.AppSystemBackHandler
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository
import org.akkirrai.hibiki.shared.player.IosComposePlayerControls
import org.akkirrai.hibiki.shared.player.IosPlayerSession
import org.akkirrai.hibiki.shared.player.IosPlayerSurface
import org.akkirrai.hibiki.shared.player.PlaybackTransport
import org.akkirrai.hibiki.shared.player.model.PlaybackStreamType
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationWillResignActiveNotification
import kotlinx.coroutines.delay

@Composable
internal fun IosEmbeddedPlaybackHost(
    playback: PlaybackStream?,
    context: PlaybackContext,
    navigationState: AppNavigationState,
    onBack: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    settingsStore: AppSettingsStore,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
    progressRepository: PlaybackProgressRepository,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    DisposableEffect(Unit) {
        setIosPlayerLandscape(active = true)
        onDispose { setIosPlayerLandscape(active = false) }
    }
    val session = remember(playback?.sessionKey()) {
        playback?.let { stream -> IosPlayerSession(stream).also {
            it.scaleMode = settingsStore.load().videoScaleMode
            it.transport.setRate(settingsStore.load().playbackSpeed)
        } }
    }
    if (session == null || playback == null) {
        var loadingScaleMode by remember { mutableStateOf(settingsStore.load().videoScaleMode) }
        val loadingTransport = remember { IosLoadingPlaybackTransport() }
        val loadingPlayback = remember(context) {
            PlaybackStream(
                animeTitle = context.sourceTitle,
                sourceTitle = context.sourceTitle,
                episodeTitle = "Episode ${context.episodeNumber}",
                streamUrl = "",
                streamType = PlaybackStreamType.HLS,
            )
        }
        AppPlayerFrame {
            AppPlayerChrome(
                surface = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                    )
                },
                controlsEnabled = true,
                controls = {
                    IosComposePlayerControls(
                        transport = loadingTransport,
                        playback = loadingPlayback,
                        context = context,
                        scaleMode = loadingScaleMode,
                        onBack = onBack,
                        onScaleClick = {
                            loadingScaleMode = loadingScaleMode.next()
                            settingsStore.save(settingsStore.load().copy(videoScaleMode = loadingScaleMode))
                        },
                        playlistEnabled = context.episodes.isNotEmpty(),
                        onPlaylistClick = { dispatchPlayerPlaylistOpen(onOverlayEvent) },
                        hasPreviousEpisode = context.episodes.any { it.number < context.episodeNumber },
                        hasNextEpisode = context.episodes.any { it.number > context.episodeNumber },
                        onPreviousEpisode = { selectAdjacentEpisode(context, -1, onEpisodeSelected) },
                        onNextEpisode = { selectAdjacentEpisode(context, 1, onEpisodeSelected) },
                        onSettingsClick = { dispatchPlayerSettingsOpen(onOverlayEvent) },
                        settingsContentDescription = appText(AppTextKey.PlayerSettings),
                    )
                },
            )
        }
        return
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
    val playlistVisible = navigationState.isPlaylistOverlayActive
    val settingsVisible = navigationState.isPlayerSettingsOverlayActive
    var selectedSpeed by remember(session) { mutableFloatStateOf(settingsStore.load().playbackSpeed) }
    var autoSkipSegments by remember(session) { mutableStateOf(settingsStore.load().autoSkipSegments) }
    var autoPlayNextEpisode by remember(session) { mutableStateOf(settingsStore.load().autoPlayNextEpisode) }
    var controlsVisible by remember { mutableStateOf(true) }
    var playerLockState by remember(session) { mutableStateOf(org.akkirrai.hibiki.shared.player.PlayerLockState()) }
    var positionMs by remember(session) { mutableLongStateOf(0L) }
    var completionState by remember(session) { mutableStateOf(org.akkirrai.hibiki.shared.player.PlayerCompletionState()) }
    var playerSkipState by remember(context.episodeId) { mutableStateOf(org.akkirrai.hibiki.shared.player.PlayerSkipState()) }
    val episodeNavigation = resolveEpisodeNavigationAvailability(
        episodes = context.episodes,
        currentEpisodeId = context.episodeId,
        currentEpisodeNumber = context.episodeNumber,
    )

    fun savePlaybackProgress() {
        progressCoordinator.persistCurrentPosition(session.transport)
    }

    fun closePlayback() {
        dispatchPlayerClose(::savePlaybackProgress, onBack)
    }

    fun selectAdjacentEpisode(offset: Int) {
        dispatchAdjacentPlayerEpisodeSelection(
            episodes = context.episodes,
            currentEpisodeId = context.episodeId,
            currentEpisodeNumber = context.episodeNumber,
            offset = offset,
            setControlsVisible = { controlsVisible = true },
            persistProgress = ::savePlaybackProgress,
            onEpisodeSelected = onEpisodeSelected,
        )
    }

    DisposableEffect(session) {
        val backgroundObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
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
                completionHandled = completionState.isHandled,
            )?.let {
                completionState = completionState.markHandled()
                dispatchPlayerEpisodeSelection(
                    episode = it,
                    setControlsVisible = { controlsVisible = true },
                    persistProgress = ::savePlaybackProgress,
                    onEpisodeSelected = onEpisodeSelected,
                )
            }
            delay(500L)
        }
    }
    val rawActiveSkipSegment = resolveActivePlaybackSegment(playback.segments, positionMs)
        ?.takeIf { controlsVisible && !playerLockState.isLocked && !playlistVisible && !settingsVisible }
    val activeSkipSegmentKey = rawActiveSkipSegment?.let { buildSkipSegmentKey(context.episodeId, it) }
    val activeSkipSegment = rawActiveSkipSegment
        ?.takeIf {
            shouldShowSkipSegmentPrompt(
                controlsVisible = controlsVisible,
                playerLocked = playerLockState.isLocked,
                playlistVisible = playlistVisible,
                settingsVisible = settingsVisible,
            )
        }
        ?.takeIf { playerSkipState.hiddenSegmentKey != activeSkipSegmentKey }
    LaunchedEffect(activeSkipSegmentKey, settingsStore.load().autoSkipSegments) {
        val key = activeSkipSegmentKey ?: run {
            playerSkipState = playerSkipState.resetCountdown()
            return@LaunchedEffect
        }
        val segment = rawActiveSkipSegment
        playerSkipState = playerSkipState.resetCountdown()
        repeat(DefaultSkipSegmentCountdownSeconds) {
            delay(1_000L)
            if (playerSkipState.hiddenSegmentKey == key) return@LaunchedEffect
            playerSkipState = playerSkipState.tick()
        }
        if (settingsStore.load().autoSkipSegments && playerSkipState.hiddenSegmentKey != key) {
            session.transport.seekToMs(segment.endMs)
        } else if (playerSkipState.hiddenSegmentKey != key) {
            playerSkipState = playerSkipState.hide(key)
        }
    }
    AppPlayerFrame {
        AppPlayerChrome(
            surface = {
                IosPlayerSurface(session, session.scaleMode, Modifier.fillMaxSize())
            },
            controlsEnabled = !playerLockState.isLocked,
            controls = {
            IosComposePlayerControls(
                transport = session.transport,
                playback = playback,
                context = context,
                scaleMode = session.scaleMode,
                onBack = ::closePlayback,
                scaleContentDescription = appText(session.scaleMode.textKey()),
                onScaleClick = {
                    session.scaleMode = session.scaleMode.next()
                    settingsStore.save(settingsStore.load().copy(videoScaleMode = session.scaleMode))
                },
                playlistEnabled = context.episodes.isNotEmpty(),
                onPlaylistClick = {
                    dispatchPlayerPlaylistOpen(onOverlayEvent)
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
                    dispatchPlayerSettingsOpen(onOverlayEvent)
                },
                settingsContentDescription = appText(AppTextKey.PlayerSettings),
                onLockClick = {
                    dispatchPlayerLock(
                        setLocked = { playerLockState = playerLockState.lock() },
                        setControlsHidden = { controlsVisible = false },
                        playlistVisible = playlistVisible,
                        settingsVisible = settingsVisible,
                        onOverlayEvent = onOverlayEvent,
                    )
                },
                lockContentDescription = appText(AppTextKey.PlayerLock),
                onControlsVisibilityChanged = { controlsVisible = it },
                pictureInPictureEnabled = session.pictureInPictureController != null,
                onPictureInPictureClick = {
                    session.pictureInPictureController?.startPictureInPicture()
                },
                pictureInPictureContentDescription = appText(AppTextKey.PlayerPictureInPicture),
            )
            },
            overlayContent = {
                AppPlayerOverlayStack(
            lockState = playerLockState,
            unlockLabel = appText(AppTextKey.PlayerUnlock),
            onUnlock = {
                dispatchPlayerUnlock(
                    setUnlocked = { playerLockState = playerLockState.unlock() },
                    setControlsVisible = { controlsVisible = true },
                )
            },
            unlockModifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PlayerUnlockBottomPadding),
            includeSystemBottomInset = true,
        ) {
            AppPlayerPanelOverlays(
            playlistVisible = playlistVisible,
            settingsVisible = settingsVisible,
            currentEpisodeId = context.episodeId,
            episodes = context.episodes,
            episodeHeadline = { episode ->
                appText(AppTextKey.PlayerEpisodeNumber).replace("%s", formatEpisodeNumber(episode.number))
            },
            onDismissPlaylist = {
                dispatchPlayerPlaylistDismiss({ controlsVisible = true }, onOverlayEvent)
            },
            onDismissSettings = {
                dispatchPlayerSettingsDismiss({ controlsVisible = true }, onOverlayEvent)
            },
            onEpisodeClick = { episodeId ->
                context.episodes.firstOrNull { it.id == episodeId }?.let {
                    dispatchPlayerEpisodeSelection(
                        episode = it,
                        setControlsVisible = { controlsVisible = true },
                        persistProgress = ::savePlaybackProgress,
                        onEpisodeSelected = onEpisodeSelected,
                    )
                }
            },
            nowMs = { (NSDate().timeIntervalSince1970 * 1_000.0).toLong() },
            backHandler = { enabled, callback ->
                AppSystemBackHandler(enabled = enabled, onBack = callback) {}
            },
            settingsContent = {
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
                        dispatchPlayerSettingsDestination(it, { controlsVisible = true }, onOverlayEvent)
                    },
                    onBack = {
                        dispatchPlayerSettingsRoot({ controlsVisible = true }, onOverlayEvent)
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
                        dispatchPlayerSettingsSelection(
                            action = PlaybackSettingsAction.SelectVoiceover(source),
                            setControlsVisible = { controlsVisible = true },
                            persistProgress = ::savePlaybackProgress,
                            onSettingsAction = onSettingsAction,
                        )
                    },
                    onSelectPlayer = { playerName ->
                        dispatchPlayerSettingsSelection(
                            action = PlaybackSettingsAction.SelectPlayer(playerName),
                            setControlsVisible = { controlsVisible = true },
                            persistProgress = ::savePlaybackProgress,
                            onSettingsAction = onSettingsAction,
                        )
                    },
                    onSelectQuality = { qualityLabel ->
                        dispatchPlayerSettingsSelection(
                            action = PlaybackSettingsAction.SelectQuality(qualityLabel),
                            setControlsVisible = { controlsVisible = true },
                            persistProgress = ::savePlaybackProgress,
                            onSettingsAction = onSettingsAction,
                        )
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
            },
            skipVisible = activeSkipSegment != null,
            controlsVisible = controlsVisible,
            skipCountdownSeconds = playerSkipState.countdownSeconds,
            autoSkipEnabled = settingsStore.load().autoSkipSegments,
            skipLabel = appText(AppTextKey.PlayerSkip),
            watchLabel = appText(AppTextKey.PlayerWatch),
            onSkipClick = { activeSkipSegment?.let { session.transport.seekToMs(it.endMs) } },
            onWatchClick = { activeSkipSegmentKey?.let { playerSkipState = playerSkipState.hide(it) } },
                skipModifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        },
        )
    }
}

private fun selectAdjacentEpisode(
    context: PlaybackContext,
    offset: Int,
    onEpisodeSelected: (WatchEpisode) -> Unit,
) {
    val sortedEpisodes = context.episodes.sortedBy(WatchEpisode::number)
    val currentIndex = sortedEpisodes.indexOfFirst { it.id == context.episodeId }
    sortedEpisodes.getOrNull(currentIndex + offset)?.let(onEpisodeSelected)
}

private class IosLoadingPlaybackTransport : PlaybackTransport {
    private var speed = 1f

    override fun play() = Unit
    override fun pause() = Unit
    override fun setRate(rate: Float) { speed = rate }
    override fun rate(): Float = speed
    override fun positionMs(): Long = 0L
    override fun durationMs(): Long = 0L
    override fun bufferedPositionMs(): Long = 0L
    override fun seekToMs(positionMs: Long) = Unit
}
