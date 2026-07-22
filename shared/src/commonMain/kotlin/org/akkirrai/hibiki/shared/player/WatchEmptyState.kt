package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.component.AppMessageState

@Composable
fun WatchEmptyState(
    title: String,
    message: String,
    icon: ImageVector,
    retryLabel: String?,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AppMessageState(
        title = title,
        message = message,
        modifier = modifier.padding(horizontal = 24.dp),
        actionLabel = retryLabel,
        onActionClick = onRetry,
        icon = icon,
    )
}
