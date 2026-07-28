package org.akkirrai.hibiki.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.AppTopLevelScaffold
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination
import org.akkirrai.hibiki.shared.text.appText

/** Shared production shell used by platform hosts while they own screen orchestration. */
@Composable
fun AppProductionRoot(
    currentDestination: AppTopLevelDestination,
    onNavigationEvent: (AppNavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable (AppTopLevelDestination, Modifier) -> Unit = { destination, iconModifier ->
        androidx.compose.material3.Icon(
            imageVector = destination.icon,
            contentDescription = null,
            modifier = iconModifier,
        )
    },
    content: @Composable () -> Unit,
) {
    AppTopLevelScaffold(
        currentDestination = currentDestination,
        onDestinationClick = { destination ->
            onNavigationEvent(AppNavigationEvent.SelectTopLevel(destination))
        },
        iconContent = iconContent,
        label = { destination -> appText(destination.labelKey) },
        modifier = modifier,
        content = content,
    )
}
