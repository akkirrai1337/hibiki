package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when {
        isLoading && !hasContent -> AppCenteredLoading(modifier.fillMaxSize())
        errorMessage != null && !hasContent -> AppMessageState(
            title = errorTitle,
            message = errorMessage,
            modifier = modifier.fillMaxSize().padding(16.dp),
            actionLabel = retryLabel,
            onActionClick = onRetry,
            icon = errorIcon,
            iconTint = errorIconTint,
        )
        else -> content()
    }
}
