package org.akkirrai.hibiki.shared.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppMessageState

@Composable
fun HomePersonalEmptyState(
    title: String,
    message: String,
    actionLabel: String,
    icon: ImageVector,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppMessageState(
        title = title,
        message = message,
        actionLabel = actionLabel,
        onActionClick = onActionClick,
        icon = icon,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .padding(horizontal = UiDimens.ScreenPadding),
    )
}
