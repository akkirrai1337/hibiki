package org.akkirrai.hibiki.shared.home.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun AppHomeActivationEffect(
    isActive: Boolean,
    onActivated: () -> Unit,
) {
    LaunchedEffect(isActive) {
        if (isActive) onActivated()
    }
}
