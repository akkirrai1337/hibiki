package org.akkirrai.hibiki.shared.app.destination.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.akkirrai.hibiki.shared.app.destination.context.AppDestinationContentInput
import org.akkirrai.hibiki.shared.design.component.navigation.AppBottomBarContentExtraPadding
import org.akkirrai.hibiki.shared.design.component.navigation.AppBottomBarHeight
import org.akkirrai.hibiki.shared.layout.appBottomSystemInsetValue
import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.LocalAppSourceConfigContent
@Composable
internal fun AppDestinationContent(input: AppDestinationContentInput) {
    val selectedTab = input.selectedTab
    val contentState = input.watch.state
    val sourceState = input.sources.state
    val hostContext = input.platform.hostContext
    var editingSourceConfig by remember { mutableStateOf<AppSourceDescriptor?>(null) }
    val sourceConfigContent = LocalAppSourceConfigContent.current
    val homeSourcesById = remember(sourceState.sources) { sourceState.sources.associateBy(AppSourceDescriptor::id) }
    val bottomSystemInset = appBottomSystemInsetValue(hostContext.includeNavigationBarPadding)
    val topLevelBottomContentPadding = if (
        selectedTab != AppDestination.SETTINGS && contentState.currentRoute is AppRoute.TopLevel
    ) {
        AppBottomBarHeight + bottomSystemInset + AppBottomBarContentExtraPadding
    } else {
        bottomSystemInset
    }
    if (contentState.isWatchRouteDriven() && contentState.watchAnime != null) {
        AppDestinationWatchRoute(input)
        return
    }
    if (contentState.isDetailsRouteDriven() && contentState.selectedAnime != null) {
        AppDestinationDetailsRoute(input)
        return
    }

    AppDestinationTopLevelRoutes(
        input = input,
        selectedTab = selectedTab,
        topLevelBottomContentPadding = topLevelBottomContentPadding,
        homeSourcesById = homeSourcesById,
        editingSourceConfig = editingSourceConfig,
        sourceConfigContent = sourceConfigContent,
        onEditSourceConfig = { editingSourceConfig = it },
    )
}
