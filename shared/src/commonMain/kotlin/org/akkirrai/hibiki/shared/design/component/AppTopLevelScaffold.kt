package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination

/** Places a top-level screen above the shared bottom navigation surface. */
@Composable
fun AppTopLevelScaffold(
    currentDestination: AppTopLevelDestination,
    onDestinationClick: (AppTopLevelDestination) -> Unit,
    iconContent: @Composable (AppTopLevelDestination, Modifier) -> Unit,
    label: @Composable (AppTopLevelDestination) -> String,
    destinations: List<AppTopLevelDestination> = AppTopLevelDestination.entries,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
        AppBottomBar(
            destinations = destinations,
            currentDestination = currentDestination,
            onDestinationClick = onDestinationClick,
            iconContent = iconContent,
            label = label,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
