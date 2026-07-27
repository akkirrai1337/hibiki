package org.akkirrai.hibiki.shared.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.akkirrai.hibiki.shared.design.UiDimens

@Composable
fun AppLibraryEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    iconContent: @Composable (Modifier) -> Unit,
) {
    LibraryEmptyState(
        title = title,
        message = message,
        modifier = modifier.padding(horizontal = UiDimens.ScreenPadding),
        iconContent = {
            Box(
                modifier = Modifier
                    .size(LibraryEmptyStateIconContainerSize)
                    .clip(RoundedCornerShape(LibraryEmptyStateIconCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                iconContent(Modifier.size(LibraryEmptyStateIconSize))
            }
        },
    )
}
