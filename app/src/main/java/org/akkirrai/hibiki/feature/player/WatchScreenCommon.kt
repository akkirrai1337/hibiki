package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import org.akkirrai.hibiki.R

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
    org.akkirrai.hibiki.shared.player.WatchScreenScaffold(
        onBackClick = onBackClick,
        backEnabled = !navigationLocked,
        backContentDescription = stringResource(R.string.cd_back),
        modifier = modifier,
        content = { _ -> content() },
    )
}

