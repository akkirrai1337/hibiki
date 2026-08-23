package org.akkirrai.hibiki.home.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.design.component.state.AppContentState
import org.akkirrai.hibiki.design.component.state.AppErrorState
import org.akkirrai.hibiki.design.component.state.AppMessageState

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

@Composable
fun AppHomeFilterCatalogState(
    isLoading: Boolean,
    unavailableLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(UiDimens.FilterCatalogStateHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Text(
                text = unavailableLabel,
                modifier = Modifier.padding(UiDimens.FilterCatalogUnavailablePadding),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun HomePersonalEmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppMessageState(
        title = title,
        message = message,
        actionLabel = actionLabel,
        onActionClick = onActionClick,
        icon = Icons.Outlined.VideoLibrary,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .padding(horizontal = UiDimens.ScreenPadding),
    )
}

@Composable
private fun AppHomeErrorIconContainer(
    modifier: Modifier = Modifier,
    iconContent: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center,
    ) {
        iconContent()
    }
}
