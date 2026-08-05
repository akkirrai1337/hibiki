package org.akkirrai.hibiki.shared.home.state

import org.akkirrai.hibiki.shared.home.screen.AppHomeErrorIconContainer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.state.AppErrorState

@Composable
fun HomeErrorState(
    title: String,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    iconContent: @Composable () -> Unit = {
        AppHomeErrorIconContainer {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    },
    modifier: Modifier = Modifier,
) {
    AppErrorState(
        title = title,
        message = message,
        retryLabel = retryLabel,
        onRetry = onRetry,
        modifier = modifier,
        iconContent = iconContent,
    )
}
