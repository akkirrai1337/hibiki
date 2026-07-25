package org.akkirrai.hibiki.shared.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun DetailsStatusBarScrim(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val distanceUntilOpaquePx = with(density) { 168.dp.toPx() }
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val alpha by remember(listState, distanceUntilOpaquePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                0.74f
            } else {
                0.74f * (listState.firstVisibleItemScrollOffset / distanceUntilOpaquePx)
                    .coerceIn(0f, 1f)
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusBarHeight)
            .background(MaterialTheme.colorScheme.background.copy(alpha = alpha)),
    )
}
