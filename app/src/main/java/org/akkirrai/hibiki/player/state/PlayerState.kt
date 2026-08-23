package org.akkirrai.hibiki.player

import org.akkirrai.hibiki.player.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.player.model.PlaybackStream
import org.akkirrai.hibiki.player.model.WatchEpisode

data class PlayerUiState(
    val isLoading: Boolean = true,
    val playback: PlaybackStream? = null,
    val animeTitle: String = "",
    val episodes: List<WatchEpisode> = emptyList(),
    val currentSourceId: String = "",
    val currentEpisodeId: String = "",
    val currentEpisodeNumber: Double? = null,
    val lastPlaybackRequest: PlaybackRequest? = null,
    val pendingSeekMs: Long = 0L,
    val errorMessage: String? = null,
    val failedStreamUrls: Set<String> = emptySet(),
    val recoveryAttempted: Boolean = false,
    val isSettingsLoading: Boolean = false,
    val settingsOptions: PlaybackSettingsOptions = PlaybackSettingsOptions(),
    val settingsOptionsKey: String? = null,
    val selectedPlayerName: String? = null,
    val selectedQualityLabel: String? = null,
)

data class PlaybackTransportState(
    val positionMs: Long,
    val durationMs: Long,
    val bufferedPositionMs: Long,
    val isPlaying: Boolean,
)

fun PlaybackTransport.readState(): PlaybackTransportState = PlaybackTransportState(
    positionMs = positionMs(),
    durationMs = durationMs(),
    bufferedPositionMs = bufferedPositionMs(),
    isPlaying = rate() > 0f,
)

/** Common guard preventing auto-play completion from firing more than once per episode. */
data class PlayerCompletionState(
    val isHandled: Boolean = false,
) {
    fun markHandled(): PlayerCompletionState = copy(isHandled = true)

    fun reset(): PlayerCompletionState = PlayerCompletionState()
}

/** Shared lock/unlock state for the common player controls. */
data class PlayerLockState(
    val isLocked: Boolean = false,
    val isUnlockButtonVisible: Boolean = false,
) {
    fun lock(): PlayerLockState = copy(
        isLocked = true,
        isUnlockButtonVisible = true,
    )

    fun unlock(): PlayerLockState = PlayerLockState()
}

const val DefaultSkipSegmentCountdownSeconds = 10

/** Common state for the opening/ending skip prompt across media hosts. */
data class PlayerSkipState(
    val hiddenSegmentKey: String? = null,
    val countdownSeconds: Int = DefaultSkipSegmentCountdownSeconds,
) {
    fun resetCountdown(): PlayerSkipState = copy(
        countdownSeconds = DefaultSkipSegmentCountdownSeconds,
    )

    fun tick(): PlayerSkipState = copy(
        countdownSeconds = (countdownSeconds - 1).coerceAtLeast(0),
    )

    fun hide(segmentKey: String): PlayerSkipState = copy(hiddenSegmentKey = segmentKey)
}
