package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppBackButton
import org.akkirrai.hibiki.core.design.component.AppMessageState

@Composable
internal fun rememberWatchNavigationLockState(
    lifecycleOwner: LifecycleOwner,
): MutableState<Boolean> {
    val navigationLocked = remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                navigationLocked.value = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return navigationLocked
}

@Composable
internal fun WatchScreenScaffold(
    onBackClick: () -> Unit,
    navigationLocked: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        content()

        WatchBackButton(
            onBackClick = onBackClick,
            enabled = !navigationLocked,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(
                    start = UiDimens.ScreenPadding,
                    top = 8.dp
                )
        )
    }
}

@Composable
internal fun WatchBackButton(
    onBackClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    AppBackButton(
        onClick = onBackClick,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
internal fun WatchEmptyState(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    AppMessageState(
        title = title,
        message = message,
        icon = icon,
        modifier = modifier.padding(horizontal = 24.dp),
        actionLabel = onRetry?.let { stringResource(R.string.search_retry) },
        onActionClick = onRetry,
    )
}
