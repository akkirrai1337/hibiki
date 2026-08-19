package org.akkirrai.hibiki.player

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString

@Composable
fun AppEpisodeDownloadRow(
    headline: AnnotatedString,
    subtitle: String?,
    inProgress: Boolean,
    enabled: Boolean,
    showDownloadAction: Boolean,
    downloadState: EpisodeDownloadActionState,
    controlsEnabled: Boolean,
    downloadedContentDescription: String,
    downloadContentDescription: String,
    pauseContentDescription: String,
    resumeContentDescription: String,
    removeContentDescription: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    EpisodeRow(
        headline = headline,
        subtitle = subtitle,
        inProgress = inProgress,
        enabled = enabled,
        showDownloadAction = showDownloadAction,
        shape = shape,
        onClick = onClick,
        downloadAction = {
            AppEpisodeDownloadAction(
                state = downloadState,
                controlsEnabled = controlsEnabled,
                downloadedContentDescription = downloadedContentDescription,
                downloadContentDescription = downloadContentDescription,
                pauseContentDescription = pauseContentDescription,
                resumeContentDescription = resumeContentDescription,
                removeContentDescription = removeContentDescription,
                onDownloadClick = onDownloadClick,
                onPauseClick = onPauseClick,
                onResumeClick = onResumeClick,
                onRemoveClick = onRemoveClick,
            )
        },
    )
}
