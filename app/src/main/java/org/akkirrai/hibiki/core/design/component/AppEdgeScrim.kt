package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppEdgeScrimDefaults {
    val TopHeight: Dp = 88.dp
    val BottomHeight: Dp = 124.dp

    @Composable
    fun topBrush(): Brush {
        return Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = 0.76f),
                0.56f to Color.Black.copy(alpha = 0.38f),
                1f to Color.Black.copy(alpha = 0f),
            ),
        )
    }

    @Composable
    fun bottomBrush(): Brush {
        return Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = 0f),
                0.52f to Color.Black.copy(alpha = 0.34f),
                1f to Color.Black.copy(alpha = 0.78f),
            ),
        )
    }
}

@Composable
fun AppTopScrim(
    modifier: Modifier = Modifier,
    height: Dp = AppEdgeScrimDefaults.TopHeight,
    brush: Brush = AppEdgeScrimDefaults.topBrush(),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(brush),
    )
}

@Composable
fun AppBottomScrim(
    modifier: Modifier = Modifier,
    height: Dp = AppEdgeScrimDefaults.BottomHeight,
    brush: Brush = AppEdgeScrimDefaults.bottomBrush(),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(brush),
    )
}
