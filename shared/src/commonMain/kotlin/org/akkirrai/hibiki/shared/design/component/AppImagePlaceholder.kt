package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun AppImagePlaceholder(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    AppTonalSurface(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
