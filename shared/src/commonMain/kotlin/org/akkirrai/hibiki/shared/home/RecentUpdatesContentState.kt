package org.akkirrai.hibiki.shared.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.AppContentState

@Composable
fun AppRecentUpdatesContentState(
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
        modifier = modifier,
        content = content,
    )
}
