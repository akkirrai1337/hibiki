package org.akkirrai.hibiki.shared.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ProfileDonutSegment(val weight: Float, val color: Color)

@Composable
fun ProfileSegmentDonut(
    segments: List<ProfileDonutSegment>,
    centerPrimary: String,
    centerSecondary: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val trackColor = if (muted) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.58f) else MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val strokeWidth = 18.dp.toPx()
            drawArc(trackColor, -90f, 360f, false, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            var startAngle = -90f
            val safeTotal = segments.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
            segments.filter { it.weight > 0f }.forEach { segment ->
                val sweep = segment.weight / safeTotal * 360f
                drawArc(if (muted) segment.color.copy(alpha = 0.4f) else segment.color, startAngle, sweep, false, style = Stroke(strokeWidth, cap = StrokeCap.Round))
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Text(centerSecondary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f))
        }
    }
}
