package org.akkirrai.hibiki.shared.catalog.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.state.AppCenteredLoading

@Composable
fun AppCatalogRefreshingState(
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isRefreshing) {
        AppCenteredLoading(modifier = modifier)
    }
}
