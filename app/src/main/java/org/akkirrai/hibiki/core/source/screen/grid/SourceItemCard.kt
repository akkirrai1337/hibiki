package org.akkirrai.hibiki.core.source

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight

@Composable
fun SourceItemCard(
    name: String,
    selected: Boolean,
    shape: RoundedCornerShape,
    iconContent: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SourceItemCardHorizontalPadding,
                vertical = SourceItemCardVerticalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconContent()
            Spacer(modifier = Modifier.width(SourceItemCardIconTextGap))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            SourceSelectionIndicator(
                selected = selected,
                modifier = Modifier.size(SourceItemCardSelectionIndicatorSize),
            )
        }
    }
}

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
