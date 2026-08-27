package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// No circular chip behind these -- just the glyph, tinted by active/inactive state, in a click
// box a bit larger than the glyph itself so tapping still feels comfortable without reading as a
// filled button.
private val DownloadActionSize = 40.dp
private val DownloadIconSize = 22.dp
private val DownloadProgressStrokeWidth = 2.dp

@Composable
internal fun WatchDownloadIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(DownloadActionSize)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(DownloadIconSize),
            tint = active.downloadIconTint(),
        )
    }
}

@Composable
internal fun WatchDownloadStateIcon(
    icon: ImageVector,
    contentDescription: String,
) {
    Box(
        modifier = Modifier.size(DownloadActionSize),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(DownloadIconSize),
            tint = true.downloadIconTint(),
        )
    }
}

@Composable
internal fun WatchDownloadProgressBadge(
    progress: Float,
) {
    Box(
        modifier = Modifier.size(DownloadActionSize),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(DownloadIconSize),
            strokeWidth = DownloadProgressStrokeWidth,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun Boolean.downloadIconTint() =
    if (this) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
