package org.akkirrai.hibiki.shared.home

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.vector.ImageVector

fun LazyListScope.appHomePersonalEmptySection(
    visible: Boolean,
    title: String,
    message: String,
    actionLabel: String,
    icon: ImageVector,
    onActionClick: () -> Unit,
) {
    if (!visible) return

    item {
        HomePersonalEmptyState(
            title = title,
            message = message,
            actionLabel = actionLabel,
            icon = icon,
            onActionClick = onActionClick,
        )
    }
}
