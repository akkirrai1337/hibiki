package org.akkirrai.hibiki.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

// No circular chip behind these anymore -- just the glyph, tinted by active/inactive state, in a
// click box a bit larger than the glyph itself so tapping still feels comfortable without reading
// as a filled button. See EpisodeDownloadActionSize/EpisodeDownloadIconSize.

@Composable
fun DownloadIconButton(icon: ImageVector, contentDescription: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(EpisodeDownloadActionSize)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription,
            Modifier.size(EpisodeDownloadIconSize),
            tint = active.iconTint(),
        )
    }
}

@Composable
fun DownloadStateIcon(icon: ImageVector, contentDescription: String) {
    Box(Modifier.size(EpisodeDownloadActionSize), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, Modifier.size(EpisodeDownloadIconSize), tint = true.iconTint())
    }
}

@Composable
fun EpisodeDownloadedIcon(contentDescription: String) {
    DownloadStateIcon(Icons.Outlined.Check, contentDescription)
}

@Composable
fun EpisodeDownloadIcon(contentDescription: String, onClick: () -> Unit) {
    DownloadIconButton(Icons.Outlined.Download, contentDescription, active = false, onClick = onClick)
}

@Composable
fun EpisodePauseIcon(contentDescription: String, onClick: () -> Unit) {
    DownloadIconButton(Icons.Outlined.Pause, contentDescription, active = true, onClick = onClick)
}

@Composable
fun EpisodeResumeIcon(contentDescription: String, onClick: () -> Unit) {
    DownloadIconButton(Icons.Outlined.PlayArrow, contentDescription, active = true, onClick = onClick)
}

@Composable
fun EpisodeRemoveDownloadIcon(contentDescription: String, onClick: () -> Unit) {
    DownloadIconButton(Icons.Outlined.Delete, contentDescription, active = true, onClick = onClick)
}

@Composable
fun DownloadProgressBadge(progress: Float) {
    Box(Modifier.size(EpisodeDownloadActionSize), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(EpisodeDownloadIconSize),
            strokeWidth = EpisodeDownloadProgressStrokeWidth,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun Boolean.iconTint() =
    if (this) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
