package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun WatchDownloadIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    org.akkirrai.hibiki.shared.player.DownloadIconButton(icon, contentDescription, active, onClick)
}

@Composable
internal fun WatchDownloadStateIcon(
    icon: ImageVector,
    contentDescription: String,
) {
    org.akkirrai.hibiki.shared.player.DownloadStateIcon(icon, contentDescription)
}

@Composable
internal fun WatchDownloadProgressBadge(
    progress: Float,
) {
    org.akkirrai.hibiki.shared.player.DownloadProgressBadge(progress)
}

@Composable
private fun WatchDownloadBadge(
    active: Boolean,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f)
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        content = content,
    )
}
