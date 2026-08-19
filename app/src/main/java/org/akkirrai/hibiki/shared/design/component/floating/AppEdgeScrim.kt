package org.akkirrai.hibiki.shared.design.component.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppEdgeScrimDefaults {
    val TopHeight: Dp = 88.dp

    @Composable
    fun topBrush(): Brush {
        val surface = MaterialTheme.colorScheme.surface
        return Brush.verticalGradient(colorStops = arrayOf(
            0f to surface.copy(alpha = 0.68f),
            0.56f to surface.copy(alpha = 0.34f),
            1f to surface.copy(alpha = 0f),
        ))
    }
}

@Composable
fun AppTopScrim(modifier: Modifier = Modifier, height: Dp = AppEdgeScrimDefaults.TopHeight, brush: Brush = AppEdgeScrimDefaults.topBrush()) {
    Box(modifier.fillMaxWidth().height(height).background(brush))
}
