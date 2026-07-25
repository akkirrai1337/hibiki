package org.akkirrai.hibiki.shared.design.component

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppBackButton(
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        iconContent()
    }
}
