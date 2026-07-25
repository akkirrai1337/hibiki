package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
fun AppErrorState(
    title: String,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    iconContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle? = null,
) {
    AppMessageState(
        title = title,
        message = message,
        modifier = modifier.fillMaxSize().padding(16.dp),
        actionLabel = retryLabel,
        onActionClick = onRetry,
        iconSlot = iconContent,
        titleStyle = titleStyle ?: androidx.compose.material3.MaterialTheme.typography.titleMedium,
    )
}
