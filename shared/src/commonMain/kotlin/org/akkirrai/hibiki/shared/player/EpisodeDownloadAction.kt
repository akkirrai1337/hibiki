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

fun EpisodeDownloadActionState.forDisplay(controlsEnabled: Boolean): EpisodeDownloadActionState =
    if (!controlsEnabled && this == EpisodeDownloadActionState.Failed) {
        EpisodeDownloadActionState.NotDownloaded
    } else {
        this
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
    downloadedContentDescription: String,
    downloadContentDescription: String,
    pauseContentDescription: String,
    resumeContentDescription: String,
    removeContentDescription: String,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    if (!controlsEnabled) {
        if (state == EpisodeDownloadActionState.Completed) {
            EpisodeDownloadedIcon(downloadedContentDescription)
        }
        return
    }

    when (state) {
        EpisodeDownloadActionState.NotDownloaded,
        EpisodeDownloadActionState.Failed -> EpisodeDownloadIcon(downloadContentDescription, onDownloadClick)
        EpisodeDownloadActionState.Queued -> Row(horizontalArrangement = Arrangement.spacedBy(EpisodeDownloadActionGap)) {
            EpisodePauseIcon(pauseContentDescription, onPauseClick)
            EpisodeRemoveDownloadIcon(removeContentDescription, onRemoveClick)
        }
        is EpisodeDownloadActionState.Downloading -> Row(
            horizontalArrangement = Arrangement.spacedBy(EpisodeDownloadActionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadProgressBadge(state.progress)
            EpisodePauseIcon(pauseContentDescription, onPauseClick)
            EpisodeRemoveDownloadIcon(removeContentDescription, onRemoveClick)
        }
        EpisodeDownloadActionState.Paused -> Row(horizontalArrangement = Arrangement.spacedBy(EpisodeDownloadActionGap)) {
            EpisodeResumeIcon(resumeContentDescription, onResumeClick)
            EpisodeRemoveDownloadIcon(removeContentDescription, onRemoveClick)
        }
        EpisodeDownloadActionState.Completed -> Row(horizontalArrangement = Arrangement.spacedBy(EpisodeDownloadActionGap)) {
            EpisodeDownloadedIcon(downloadedContentDescription)
            EpisodeRemoveDownloadIcon(removeContentDescription, onRemoveClick)
        }
    }
}
