package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun DownloadIconButton(icon: ImageVector, contentDescription: String, active: Boolean, onClick: () -> Unit) {
    DownloadBadge(active = active) {
        Box(
            modifier = Modifier
                .size(EpisodeDownloadActionSize)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, Modifier.size(EpisodeDownloadIconSize))
        }
    }
}

@Composable
fun DownloadStateIcon(icon: ImageVector, contentDescription: String) {
    DownloadBadge(active = true) {
        Box(Modifier.size(EpisodeDownloadActionSize), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, Modifier.size(EpisodeDownloadIconSize))
        }
    }
}

@Composable
fun DownloadProgressBadge(progress: Float) {
    DownloadBadge(active = true) {
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
}

@Composable
private fun DownloadBadge(active: Boolean, content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f),
        contentColor = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        content = content,
    )
}
