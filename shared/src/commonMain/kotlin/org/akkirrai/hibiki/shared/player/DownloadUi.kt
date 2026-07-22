package org.akkirrai.hibiki.shared.player

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
fun DownloadIconButton(icon: ImageVector, contentDescription: String, active: Boolean, onClick: () -> Unit) {
    DownloadBadge(active = active) {
        IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
            Icon(icon, contentDescription, Modifier.size(21.dp))
        }
    }
}

@Composable
fun DownloadStateIcon(icon: ImageVector, contentDescription: String) {
    DownloadBadge(active = true) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, Modifier.size(21.dp))
        }
    }
}

@Composable
fun DownloadProgressBadge(progress: Float) {
    DownloadBadge(active = true) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
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
