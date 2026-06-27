package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import org.akkirrai.hibiki.core.design.UiDimens

@Composable
fun AppTonalSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(UiDimens.MediumCorner),
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = if (onClick != null) {
            modifier.clickable(enabled = enabled, onClick = onClick)
        } else {
            modifier
        },
        shape = shape,
        color = color,
    ) {
        Box(contentAlignment = contentAlignment) {
            content()
        }
    }
}
