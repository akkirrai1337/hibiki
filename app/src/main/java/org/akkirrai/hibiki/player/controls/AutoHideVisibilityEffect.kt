package org.akkirrai.hibiki.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
fun AppAutoHideVisibilityEffect(
    enabled: Boolean,
    visible: Boolean,
    interactionTick: Int,
    blocked: Boolean,
    hideDelayMillis: Long,
    onHide: () -> Unit,
) {
    LaunchedEffect(enabled, visible, interactionTick, blocked) {
        if (!enabled || !visible || blocked) return@LaunchedEffect
        delay(hideDelayMillis)
        onHide()
    }
}
