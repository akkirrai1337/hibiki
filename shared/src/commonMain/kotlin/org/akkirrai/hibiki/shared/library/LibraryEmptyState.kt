package org.akkirrai.hibiki.shared.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.component.AppMessageState

@Composable
fun LibraryEmptyState(
    title: String,
    message: String,
    iconContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppMessageState(
        title = title,
        message = message,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 42.dp),
        titleStyle = MaterialTheme.typography.titleLarge,
        messageModifier = Modifier.padding(top = 6.dp, start = 28.dp, end = 28.dp),
        messageMaxLines = 2,
        messageOverflow = TextOverflow.Ellipsis,
        iconSlot = iconContent,
    )
}
