package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun SourceSelectionIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Canvas(modifier = modifier) {
        drawCircle(color = color, style = Stroke(width = SourceSelectionIndicatorStrokeWidth.toPx()))
        if (selected) {
            drawCircle(color = color, radius = size.minDimension * 0.25f)
        }
    }
}
