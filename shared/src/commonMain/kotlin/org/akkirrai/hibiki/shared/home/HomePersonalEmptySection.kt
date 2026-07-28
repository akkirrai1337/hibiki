package org.akkirrai.hibiki.shared.home

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary

fun LazyListScope.appHomePersonalEmptySection(
    visible: Boolean,
    title: String,
    message: String,
    actionLabel: String,
    onActionClick: () -> Unit,
) {
    if (!visible) return

    item {
        HomePersonalEmptyState(
            title = title,
            message = message,
            actionLabel = actionLabel,
            icon = Icons.Outlined.VideoLibrary,
            onActionClick = onActionClick,
        )
    }
}
