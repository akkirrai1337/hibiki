package org.akkirrai.hibiki.player

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

fun EpisodeDownloadActionState.shouldShowAction(controlsEnabled: Boolean): Boolean =
    controlsEnabled || this == EpisodeDownloadActionState.Completed

data class EpisodeDownloadActions(
    val onClick: () -> Unit,
    val onDownloadClick: () -> Unit,
    val onPauseClick: () -> Unit,
    val onResumeClick: () -> Unit,
    val onRemoveClick: () -> Unit,
)

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
    actions: EpisodeDownloadActions,
) {
    // Only Completed rows stay visible with controls off (shouldShowAction()), swapping to this
    // simplified checkmark-only look -- anything else is hidden by the caller's AnimatedVisibility,
    // which needs this to keep rendering the *same* icon it had while collapsing away, not vanish
    // the instant controlsEnabled flips, or its shrink animation plays over empty space.
    if (!controlsEnabled && state == EpisodeDownloadActionState.Completed) {
        EpisodeDownloadedIcon(downloadedContentDescription)
        return
    }

    when (state) {
        EpisodeDownloadActionState.NotDownloaded,
        EpisodeDownloadActionState.Failed -> EpisodeDownloadIcon(downloadContentDescription, actions.onDownloadClick)
        EpisodeDownloadActionState.Queued -> Row(horizontalArrangement = Arrangement.spacedBy(EpisodeDownloadActionGap)) {
            EpisodePauseIcon(pauseContentDescription, actions.onPauseClick)
            EpisodeRemoveDownloadIcon(removeContentDescription, actions.onRemoveClick)
        }
        is EpisodeDownloadActionState.Downloading -> Row(
            horizontalArrangement = Arrangement.spacedBy(EpisodeDownloadActionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadProgressBadge(state.progress)
            EpisodePauseIcon(pauseContentDescription, actions.onPauseClick)
            EpisodeRemoveDownloadIcon(removeContentDescription, actions.onRemoveClick)
        }
        EpisodeDownloadActionState.Paused -> Row(horizontalArrangement = Arrangement.spacedBy(EpisodeDownloadActionGap)) {
            EpisodeResumeIcon(resumeContentDescription, actions.onResumeClick)
            EpisodeRemoveDownloadIcon(removeContentDescription, actions.onRemoveClick)
        }
        EpisodeDownloadActionState.Completed -> Row(horizontalArrangement = Arrangement.spacedBy(EpisodeDownloadActionGap)) {
            EpisodeDownloadedIcon(downloadedContentDescription)
            EpisodeRemoveDownloadIcon(removeContentDescription, actions.onRemoveClick)
        }
    }
}
