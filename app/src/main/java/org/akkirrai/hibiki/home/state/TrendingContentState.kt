package org.akkirrai.hibiki.home.state

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.design.component.state.AppContentState

@Composable
fun AppTrendingContentState(
    isLoading: Boolean,
    hasContent: Boolean,
    errorMessage: String?,
    errorTitle: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AppContentState(
        isLoading = isLoading,
        hasContent = hasContent,
        errorMessage = errorMessage,
        errorTitle = errorTitle,
        retryLabel = retryLabel,
        onRetry = onRetry,
        errorIcon = Icons.Outlined.WarningAmber,
        errorIconTint = MaterialTheme.colorScheme.error,
        modifier = modifier,
        content = content,
    )
}
