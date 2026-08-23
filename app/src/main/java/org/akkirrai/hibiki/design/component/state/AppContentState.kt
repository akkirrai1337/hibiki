package org.akkirrai.hibiki.design.component.state

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.design.UiDimens

@Composable
fun AppContentState(
    isLoading: Boolean,
    hasContent: Boolean,
    errorMessage: String?,
    errorTitle: String,
    retryLabel: String,
    onRetry: () -> Unit,
    errorIcon: ImageVector? = null,
    errorIconTint: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
    errorScrollable: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when {
        isLoading && !hasContent -> AppCenteredLoading(modifier.fillMaxSize())
        errorMessage != null && !hasContent -> AppMessageState(
            title = errorTitle,
            message = errorMessage,
            modifier = modifier
                .fillMaxSize()
                .padding(UiDimens.ContentStatePadding)
                .then(if (errorScrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            actionLabel = retryLabel,
            onActionClick = onRetry,
            icon = errorIcon,
            iconTint = errorIconTint,
        )
        else -> content()
    }
}

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
