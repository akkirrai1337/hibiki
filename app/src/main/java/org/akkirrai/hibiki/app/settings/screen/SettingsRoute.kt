package org.akkirrai.hibiki.app.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
internal fun SettingsRoute(
    state: SettingsScreenState,
    actions: SettingsScreenActions,
    listState: LazyListState,
    bottomContentPadding: Dp,
) {
    SettingsScreen(
        state = state,
        actions = actions,
        listState = listState,
        modifier = Modifier.fillMaxSize(),
        bottomContentPadding = bottomContentPadding,
    )
}
