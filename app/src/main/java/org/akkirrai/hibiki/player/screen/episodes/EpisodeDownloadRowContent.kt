package org.akkirrai.hibiki.player

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import org.akkirrai.hibiki.player.model.EpisodeProgressStatus
import org.akkirrai.hibiki.player.model.EpisodeWatchProgress
import org.akkirrai.hibiki.player.model.WatchEpisode

@Composable
fun AppEpisodeDownloadRowContent(
    episode: WatchEpisode,
    progress: EpisodeWatchProgress?,
    status: EpisodeProgressStatus,
    downloadState: EpisodeDownloadActionState,
    showDownloadControls: Boolean,
    shape: RoundedCornerShape,
    enabled: Boolean,
    watchedHeadline: @Composable (String) -> String,
    defaultHeadline: @Composable (String) -> String,
    watchedLabel: String,
    queuedLabel: String,
    downloadingLabel: @Composable (Int) -> String,
    pausedLabel: String,
    downloadedLabel: String,
    failedLabel: String,
    downloadedContentDescription: String,
    downloadContentDescription: String,
    pauseContentDescription: String,
    resumeContentDescription: String,
    removeContentDescription: String,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val visibleDownloadState = downloadState.forDisplay(showDownloadControls)
    val downloadingProgress = (visibleDownloadState as? EpisodeDownloadActionState.Downloading)?.progress ?: 0f
    AppEpisodeDownloadRow(
        headline = buildEpisodeRowHeadline(
            episode = episode,
            progress = progress,
            status = status,
            watchedHeadline = watchedHeadline,
            defaultHeadline = defaultHeadline,
            watchedLabel = watchedLabel,
        ),
        subtitle = resolveEpisodeDownloadSubtitle(
            state = visibleDownloadState,
            queuedLabel = queuedLabel,
            downloadingLabel = downloadingLabel((downloadingProgress * 100).toInt()),
            pausedLabel = pausedLabel,
            downloadedLabel = downloadedLabel,
            failedLabel = failedLabel,
        ).takeIf(String::isNotBlank),
        inProgress = status == EpisodeProgressStatus.InProgress,
        enabled = enabled,
        showDownloadAction = downloadState.shouldShowAction(showDownloadControls),
        shape = shape,
        onClick = onClick,
        downloadState = downloadState,
        controlsEnabled = showDownloadControls,
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
}

@Composable
private fun AppEpisodeDownloadRow(
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
