package org.akkirrai.hibiki.shared.home.screen

import org.akkirrai.hibiki.shared.home.state.HomePersonalEmptyState

import androidx.compose.foundation.lazy.LazyListScope

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
            onActionClick = onActionClick,
        )
    }
}
