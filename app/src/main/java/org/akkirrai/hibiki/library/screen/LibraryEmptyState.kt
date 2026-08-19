package org.akkirrai.hibiki.library.screen
import org.akkirrai.hibiki.library.*

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.akkirrai.hibiki.design.component.state.AppMessageState

@Composable
fun LibraryEmptyState(
    title: String,
    message: String,
    iconContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppMessageState(
        title = title,
        message = message,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = LibraryEmptyStateTopPadding),
        titleStyle = MaterialTheme.typography.titleLarge,
        messageModifier = Modifier.padding(
            top = LibraryEmptyStateMessageTopPadding,
            start = LibraryEmptyStateMessageHorizontalPadding,
            end = LibraryEmptyStateMessageHorizontalPadding,
        ),
        messageMaxLines = 2,
        messageOverflow = TextOverflow.Ellipsis,
        iconSlot = { iconContent(Modifier) },
    )
}
