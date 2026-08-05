package org.akkirrai.hibiki.shared.home.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.state.AppCenteredLoading

@Composable
fun AppHomeLoadingState(
    modifier: Modifier = Modifier,
) {
    AppCenteredLoading(modifier = modifier)
}
