package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

sealed interface EpisodeDownloadActionState {
    data object NotDownloaded : EpisodeDownloadActionState
    data object Queued : EpisodeDownloadActionState
    data class Downloading(val progress: Float) : EpisodeDownloadActionState
    data object Paused : EpisodeDownloadActionState
    data object Completed : EpisodeDownloadActionState
    data object Failed : EpisodeDownloadActionState
}

fun resolveEpisodeDownloadSubtitle(
    state: EpisodeDownloadActionState,
    queuedLabel: String,
    downloadingLabel: String,
    pausedLabel: String,
    downloadedLabel: String,
    failedLabel: String,
): String = when (state) {
    EpisodeDownloadActionState.NotDownloaded -> ""
    EpisodeDownloadActionState.Queued -> queuedLabel
    is EpisodeDownloadActionState.Downloading -> downloadingLabel
    EpisodeDownloadActionState.Paused -> pausedLabel
    EpisodeDownloadActionState.Completed -> downloadedLabel
    EpisodeDownloadActionState.Failed -> failedLabel
}

@Composable
fun AppEpisodeDownloadAction(
    state: EpisodeDownloadActionState,
    controlsEnabled: Boolean,
    downloadedContent: @Composable () -> Unit,
    downloadContent: @Composable (onClick: () -> Unit) -> Unit,
    pauseContent: @Composable (onClick: () -> Unit) -> Unit,
    resumeContent: @Composable (onClick: () -> Unit) -> Unit,
    removeContent: @Composable (onClick: () -> Unit) -> Unit,
    progressContent: @Composable (progress: Float) -> Unit,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    if (!controlsEnabled) {
        if (state == EpisodeDownloadActionState.Completed) {
            downloadedContent()
        }
        return
    }

    when (state) {
        EpisodeDownloadActionState.NotDownloaded,
        EpisodeDownloadActionState.Failed -> downloadContent(onDownloadClick)
        EpisodeDownloadActionState.Queued -> Row(horizontalArrangement = Arrangement.spacedBy(EpisodeDownloadActionGap)) {
            pauseContent(onPauseClick)
            removeContent(onRemoveClick)
        }
        is EpisodeDownloadActionState.Downloading -> Row(
            horizontalArrangement = Arrangement.spacedBy(EpisodeDownloadActionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            progressContent(state.progress)
            pauseContent(onPauseClick)
            removeContent(onRemoveClick)
        }
        EpisodeDownloadActionState.Paused -> Row(horizontalArrangement = Arrangement.spacedBy(EpisodeDownloadActionGap)) {
            resumeContent(onResumeClick)
            removeContent(onRemoveClick)
        }
        EpisodeDownloadActionState.Completed -> Row(horizontalArrangement = Arrangement.spacedBy(EpisodeDownloadActionGap)) {
            downloadedContent()
            removeContent(onRemoveClick)
        }
    }
}
