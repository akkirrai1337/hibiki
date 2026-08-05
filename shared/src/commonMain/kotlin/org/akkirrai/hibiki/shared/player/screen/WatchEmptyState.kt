package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.shared.design.component.state.AppMessageState

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
        modifier = modifier.padding(horizontal = WatchEmptyStateHorizontalPadding),
        actionLabel = retryLabel,
        onActionClick = onRetry,
        icon = icon,
    )
}
